# WorkManager constructs workers reflectively after process death and reboot.
-keep class de.simonhub.app.** extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
