import base64
import hashlib
import json
import re
import tempfile
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request
from datetime import timedelta
from pathlib import Path
from server import HubBridge, make_server, utcnow, stamp, b64


class BridgeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = str(Path(self.tmp.name) / "hub.sqlite")
        self.app, self.mcp, self.read = "a" * 48, "m" * 48, "r" * 48
        self.hub = HubBridge(self.path, self.app, self.mcp, "https://hub.example.test", self.read)
        self.state = {"date": utcnow().date().isoformat(), "zone": "UTC", "updatedAt": stamp(), "name": "Test",
                      "tasks": [{"id": "t1", "title": "Test task", "area": "STUDY", "minutes": 25, "done": False}],
                      "events": [], "goals": [], "dayStart": 0, "dayEnd": 24}
        self.hub.save_snapshot(self.state)

    def tearDown(self):
        self.hub.db.close()
        self.tmp.cleanup()

    def test_snapshot_and_freshness(self):
        self.assertTrue(self.hub.snapshot()["connected"])
        self.assertFalse(self.hub.snapshot()["stale"])
        self.state["updatedAt"] = (utcnow() - timedelta(days=1)).isoformat()
        self.hub.save_snapshot(self.state)
        self.assertTrue(self.hub.snapshot()["stale"])

    def test_snapshot_drops_unknown_secret_fields(self):
        self.hub.save_snapshot(self.state | {"secret": "must-not-be-stored"})
        self.assertNotIn("secret", self.hub.snapshot())

    def test_create_task_is_idempotent_and_requires_phone_review(self):
        args = {"request_id": "test-1", "title": "Learn EBNF", "minutes": 25}
        first = self.hub.call("hub_create_task", args)
        second = self.hub.call("hub_create_task", args)
        self.assertEqual(first["id"], second["id"])
        self.assertFalse(first["executed"])
        self.assertEqual(1, len(self.hub.actions()))
        self.assertEqual(1, len(self.hub.snapshot()["tasks"]))
        with self.assertRaises(ValueError):
            self.hub.call("hub_create_task", args | {"title": "Different task"})

    def test_ack_and_restart_do_not_duplicate_actions(self):
        result = self.hub.call("hub_complete_task", {"request_id": "done-1", "id": "t1"})
        self.hub.acknowledge([result["id"]]); self.hub.acknowledge([result["id"]])
        self.assertEqual([], self.hub.actions())
        self.hub.db.close()
        self.hub = HubBridge(self.path, self.app, self.mcp, "https://hub.example.test", self.read)
        again = self.hub.call("hub_complete_task", {"request_id": "done-1", "id": "t1"})
        self.assertEqual("delivered", again["status"])
        self.assertEqual(result["id"], again["id"])

    def test_invalid_tasks_and_unknown_ids(self):
        for args in ({"title": ""}, {"title": "x", "minutes": 0}, {"title": "x", "minutes": True}, {"title": "x", "area": "MAGIC"}, {"title": "x", "due": "2026-02-31"}):
            with self.assertRaises(ValueError):
                self.hub.call("hub_create_task", {"request_id": "invalid"} | args)
        with self.assertRaises(ValueError):
            self.hub.call("hub_complete_task", {"request_id": "missing", "id": "none"})

    def test_nonfinite_goals_and_extra_arguments_rejected(self):
        for val in (-1, float("nan"), float("inf")):
            with self.assertRaises(ValueError):
                self.hub.call("hub_set_goal", {"request_id": "goal", "id": "g", "title": "Goal", "current": val, "target": 5})
        with self.assertRaises(ValueError):
            self.hub.call("hub_get_today", {"path": "/etc/passwd"})

    def test_readonly_scope_and_token_isolation(self):
        self.assertEqual("app", self.hub.auth(self.app, app=True))
        self.assertIsNone(self.hub.auth(self.app))
        self.assertIsNone(self.hub.auth(self.mcp, app=True))
        self.assertEqual("read", self.hub.auth(self.read))
        with self.assertRaises(ValueError):
            self.hub.call("hub_create_task", {"request_id": "denied", "title": "No"}, write=False)
        response = self.hub.rpc({"jsonrpc": "2.0", "id": 1, "method": "tools/list"}, write=False)
        self.assertTrue(all(t["annotations"]["readOnlyHint"] for t in response["result"]["tools"]))

    def plan_args(self):
        a = utcnow() + timedelta(minutes=3); z = a + timedelta(minutes=5)
        if z.date() != utcnow().date():
            self.skipTest("Planning day is ending")
        return {"request_id": "plan-1", "date": self.state["date"], "blocks": [{"taskId": "t1", "start": a.isoformat(), "end": z.isoformat()}], "explanation": "Test plan"}

    def test_plan_is_idempotent(self):
        args = self.plan_args()
        a = self.hub.call("hub_set_plan", args)
        b = self.hub.call("hub_set_plan", args)
        self.assertEqual(a["id"], b["id"])

    def test_calendar_conflict_and_stale_plan_rejected(self):
        args = self.plan_args(); b = args["blocks"][0]
        self.state["events"] = [{"id": "e", "title": "Meeting", "start": b["start"], "end": b["end"], "allDay": False}]
        self.hub.save_snapshot(self.state)
        with self.assertRaises(ValueError):
            self.hub.call("hub_set_plan", args)
        self.state["events"] = []; self.state["updatedAt"] = (utcnow() - timedelta(hours=2)).isoformat(); self.hub.save_snapshot(self.state)
        with self.assertRaises(ValueError):
            self.hub.call("hub_set_plan", args)

    def test_duplicate_task_blocks_rejected(self):
        args = self.plan_args(); args["blocks"] *= 2
        with self.assertRaises(ValueError):
            self.hub.call("hub_set_plan", args)

    def oauth_consent(self, scope="hub:read hub:write"):
        client = self.hub.register({"redirect_uris": ["https://chatgpt.com/connector_platform_oauth_redirect"], "client_name": "Test MCP"})
        verifier = "v" * 64
        query = {"client_id": client["client_id"], "redirect_uri": client["redirect_uris"][0], "response_type": "code", "scope": scope,
                 "code_challenge": b64(hashlib.sha256(verifier.encode()).digest()), "code_challenge_method": "S256", "state": "test-state"}
        page = self.hub.consent_form(query)
        form = {"payload": re.search('name="payload" value="([^"]+)"', page)[1], "signature": re.search('name="signature" value="([^"]+)"', page)[1], "token": self.mcp}
        redirect = self.hub.authorize(form, "127.0.0.1")
        parsed = urllib.parse.parse_qs(urllib.parse.urlparse(redirect).query)
        self.assertEqual(["test-state"], parsed["state"])
        return client, parsed["code"][0], verifier

    def test_oauth_pkce_code_single_use_and_refresh_rotation(self):
        client, code, verifier = self.oauth_consent()
        form = {"grant_type": "authorization_code", "client_id": client["client_id"], "redirect_uri": client["redirect_uris"][0], "code": code, "code_verifier": verifier}
        with self.assertRaises(ValueError):
            self.hub.token(form | {"code_verifier": "wrong" * 16})
        tokens = self.hub.token(form)
        self.assertEqual("write", self.hub.auth(tokens["access_token"]))
        with self.assertRaises(ValueError):
            self.hub.token(form)
        refresh = {"grant_type": "refresh_token", "client_id": client["client_id"], "refresh_token": tokens["refresh_token"]}
        rotated = self.hub.token(refresh)
        self.assertIsNone(self.hub.auth(tokens["access_token"]))
        self.assertEqual("write", self.hub.auth(rotated["access_token"]))
        with self.assertRaises(ValueError):
            self.hub.token(refresh)

    def test_oauth_readonly_and_redirect_validation(self):
        client, code, verifier = self.oauth_consent("hub:read")
        token = self.hub.token({"grant_type": "authorization_code", "client_id": client["client_id"], "redirect_uri": client["redirect_uris"][0], "code": code, "code_verifier": verifier})
        self.assertEqual("read", self.hub.auth(token["access_token"]))
        with self.assertRaises(ValueError):
            self.hub.register({"redirect_uris": ["https://attacker.example/callback"]})

    def test_rpc_protocol_and_notifications(self):
        initialized = self.hub.rpc({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2025-06-18"}})
        self.assertEqual("2025-06-18", initialized["result"]["protocolVersion"])
        self.assertIsNone(self.hub.rpc({"jsonrpc": "2.0", "method": "notifications/initialized"}))
        self.assertEqual(-32601, self.hub.rpc({"jsonrpc": "2.0", "id": 1, "method": "missing"})["error"]["code"])

    def test_http_transport_auth_origin_and_oauth_metadata(self):
        server = make_server(self.hub, port=0); thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        base = "http://127.0.0.1:" + str(server.server_port)
        def request(path, data=None, token=None, origin=None):
            headers = {"Content-Type": "application/json"}
            if token: headers["Authorization"] = "Bearer " + token
            if origin: headers["Origin"] = origin
            req = urllib.request.Request(base + path, data=json.dumps(data).encode() if data is not None else None, headers=headers)
            try:
                response = opener.open(req, timeout=5)
            except urllib.error.HTTPError as error:
                response = error
            return response.status, response.read(), response.headers
        try:
            status, _, headers = request("/mcp", {"jsonrpc": "2.0", "id": 1, "method": "ping"})
            self.assertEqual(401, status); self.assertIn("resource_metadata", headers["WWW-Authenticate"])
            self.assertEqual(401, request("/v1/state", self.state, self.mcp)[0])
            self.assertEqual(200, request("/v1/state", self.state, self.app)[0])
            self.assertEqual(403, request("/mcp", {}, self.mcp, "https://attacker.example")[0])
            self.assertEqual(405, request("/mcp", token=self.mcp)[0])
            self.assertEqual(202, request("/mcp", {"jsonrpc": "2.0", "method": "notifications/initialized"}, self.mcp)[0])
            self.assertEqual(200, request("/mcp", {"jsonrpc": "2.0", "id": 1, "method": "tools/list"}, self.mcp)[0])
            status, data, _ = request("/.well-known/oauth-protected-resource")
            self.assertEqual(200, status); self.assertEqual("https://hub.example.test/mcp", json.loads(data)["resource"])
        finally:
            server.shutdown(); server.server_close(); thread.join(timeout=5)


if __name__ == "__main__":
    unittest.main(verbosity=2)
