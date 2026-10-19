package de.simonhub.integrations

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import de.simonhub.core.*
import kotlinx.coroutines.*
import java.io.*
import java.security.MessageDigest
import java.time.LocalDate

internal fun InputStream.bounded(max: Int): ByteArray {
    val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true) {val n=read(buffer,0,minOf(buffer.size,max+1-out.size()));if(n<0)break;out.write(buffer,0,n);if(out.size()>max)break}
    return out.toByteArray()
}
data class VaultSnapshot(val notes: List<HubNote>,val tasks: List<HubTask>,val limited: Boolean)
class ObsidianSource(private val context: Context) {
    suspend fun read(tree: String,importTasks: Boolean): VaultSnapshot=withContext(Dispatchers.IO) {
        val root=DocumentFile.fromTreeUri(context,Uri.parse(tree)) ?: error("Vault-Ordner nicht verfügbar.")
        check(root.exists() && root.canRead()) {"Der Ordnerzugriff fehlt. Wähle deinen Vault erneut."}
        val files=mutableListOf<Pair<String,DocumentFile>>();var limited=false;var visited=0
        fun walk(dir: DocumentFile,prefix: String,depth: Int) {
            if(depth>12 || files.size>=200 || visited>=4000) {limited=true;return}
            for(f in dir.listFiles().sortedByDescending {it.lastModified()}) {
                visited++;if(visited>4000 || files.size>=200) {limited=true;break}
                val name=f.name ?: continue;if(name.startsWith("."))continue
                val path=if(prefix.isEmpty())name else "$prefix/$name"
                if(f.isDirectory)walk(f,path,depth+1)else if(name.endsWith(".md",true)) {if(f.length()<=256*1024)files+=path to f else limited=true}
            }
        }
        walk(root,"",0);var total=0;val notes=mutableListOf<HubNote>()
        for((path,f)in files.sortedByDescending {it.second.lastModified()}) {
            val bytes=context.contentResolver.openInputStream(f.uri)?.use {it.bounded(256*1024)} ?: error("Notiz nicht lesbar: $path")
            if(bytes.size>256*1024 || total+bytes.size>4*1024*1024) {limited=true;continue}
            total+=bytes.size;val text=bytes.toString(Charsets.UTF_8)
            val title=text.lineSequence().firstOrNull {it.startsWith("# ")}?.removePrefix("# ")?.trim() ?: path.substringAfterLast('/').removeSuffix(".md")
            notes+=HubNote(path,title,text,f.lastModified())
        }
        val imported=if(importTasks)notes.asSequence().flatMap {tasks(it).asSequence()}.take(5001).toList()else emptyList()
        VaultSnapshot(notes,imported.take(5000),limited || imported.size>5000)
    }
    private fun tasks(note: HubNote): List<HubTask> {
        val regex=Regex("^\\s*[-*] \\[([ xX])\\] (.+)$");val dueRegex=Regex("📅\\s*(\\d{4}-\\d{2}-\\d{2})");val counts=mutableMapOf<String,Int>()
        return note.content.lineSequence().mapNotNull {line->
            val m=regex.matchEntire(line) ?: return@mapNotNull null;val raw=m.groupValues[2];val count=counts.getOrDefault(raw,0);counts[raw]=count+1
            val id=MessageDigest.getInstance("SHA-256").digest("${note.path}:$raw:$count".toByteArray()).joinToString("") {"%02x".format(it)}
            val due=dueRegex.find(raw)?.groupValues?.get(1)?.let {runCatching {LocalDate.parse(it)}.getOrNull()}
            HubTask(id="vault-$id",title=raw.replace(dueRegex,"").trim().take(240).ifBlank {"Aufgabe aus ${note.title}"},area=if(note.path.contains("Studium",true))Area.STUDY else Area.PERSONAL,
                due=due,done=m.groupValues[1].isNotBlank(),source=note.path,details="Import aus Obsidian. Änderungen gelten lokal in Simon Hub.")
        }.toList()
    }
}
