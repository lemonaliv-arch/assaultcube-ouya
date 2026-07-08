package net.cubers.assaultcube;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class AssetExporter {

    private static String appversioncodefile = "androidappversioncode.txt"; // keep this synchronized with C++ world in serverbrowser.cpp

    // Last time the export made forward progress (a file was copied). LaunchActivity's watchdog reads this
    // to tell a slow-but-progressing export apart from one wedged on blocked I/O (slow flash / Xposed hooks).
    public static volatile long sLastProgressMs = android.os.SystemClock.elapsedRealtime();

    public boolean isAssetExportRequired(Activity activity) {
        // Exporting assets is a heavy operation and so we skip this operation if
        // the previously exported assets are of the same version as the currently running app.
        // Essentially we export assets once each time the app has been updated to a new version.
        boolean versionsMatch = getVersionCodeOfCurrentApp().equals(getVersionCodeOfLastExportedAssets(activity));
        return !versionsMatch;
    }

    /** Receives copy progress so LaunchActivity can show it on screen (called from the export thread). */
    public interface ProgressListener {
        void onProgress(int copied, int total);
    }

    public boolean copyAssets(Activity activity, ProgressListener progress) {
        File targetDir = activity.getExternalFilesDir(null);
        if(targetDir == null) {
            // Without this guard "new File(null, filename)" silently resolves to a relative path and
            // every copy fails one by one; fail fast instead so LaunchActivity can tell the user.
            Log.e("assets", "getExternalFilesDir() returned null - external storage unavailable, cannot export assets.");
            return false;
        }
        Log.i("assets", "exporting assets to " + targetDir + " (free space: " + getFreeSpaceMb(activity) + " MB)");
        AssetManager assetManager = activity.getAssets();
        ArrayList<String> files = listAssets(assetManager);
        boolean allWentWell = true;
        int copied = 0;
        for (String filename : files) {
            sLastProgressMs = android.os.SystemClock.elapsedRealtime(); // mark progress so the watchdog knows we're alive
            InputStream in = null;
            OutputStream out = null;
            try {
                // The File:API is of no use - only *existing* paths can be checked if they're directories
                boolean isAssetContainer = false;
                try {
                    in = assetManager.open(filename);
                } catch(FileNotFoundException e) { // asset directories throw this exception and there appears to be no easier way to handle this issue
                    isAssetContainer = true;
                }
                if (!isAssetContainer){
                    File outFile = new File(activity.getExternalFilesDir(null), filename);
                    File parent = outFile.getParentFile();
                    if(parent != null) parent.mkdirs();
                    out = new FileOutputStream(outFile);
                    copyFile(in, out);
                }
            } catch(IOException e) {
                Log.e("assets", "Failed to copy asset file: " + filename, e);
                allWentWell = false;
            }
            finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // NOOP
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        // NOOP
                    }
                }
            }
            copied++;
            if(progress != null) progress.onProgress(copied, files.size());
        }
        if (allWentWell) {
            trySetVersionCodeOfLastExportedAssets(activity, getVersionCodeOfCurrentApp());
        }
        return allWentWell;
    }

    /**
     * The list of asset files to export. Read from the build-time generated index if present: on API 16
     * AssetManager.list() costs O(total assets) PER CALL, so the recursive listing of ~2900 files takes
     * MINUTES of CPU on the OUYA before the first byte is copied - the index makes it a single read.
     * Falls back to the recursion if the index asset is missing (e.g. a build without the gradle task).
     */
    private ArrayList<String> listAssets(AssetManager assetManager) {
        ArrayList<String> files = new ArrayList<>();
        try {
            InputStream in = assetManager.open("assets_index.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while((line = reader.readLine()) != null) {
                line = line.trim();
                if(!line.isEmpty()) files.add(line);
            }
            reader.close();
            Log.i("assets", "asset index lists " + files.size() + " files.");
            return files;
        } catch(IOException e) {
            Log.w("assets", "no asset index bundled; falling back to slow AssetManager.list() recursion.");
        }
        recursiveList(assetManager, "", files);
        return files;
    }

    private void recursiveList(AssetManager assetManager, String path, ArrayList<String> output)
    {
        try {
            // AssetManager.list() is slow (O(n) per call, ~2500 calls for the AC assets). On a slow
            // OUYA the whole listing can take longer than the stall watchdog's threshold, and no file
            // copy has bumped the timestamp yet - so mark progress here too or the watchdog would
            // launch the game before the export even started.
            sLastProgressMs = android.os.SystemClock.elapsedRealtime();
            String[] children = assetManager.list(path);
            for(String child : children)
            {
                String childpath = (path.equals("") ? "" : path + "/") + child;
                output.add(childpath);
                recursiveList(assetManager, childpath, output);
            }
        } catch (IOException e) {
            Log.e("assets", "Failed to get asset file list.", e);
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        // OUYA: a 1KB buffer meant ~50k read/write iterations to export the ~50MB of assets, which on the
        // OUYA's slow flash made the "Please wait..." splash take minutes. A 64KB buffer cuts that to a few
        // hundred iterations (and we buffer the output stream), so a re-export now finishes in seconds.
        byte[] buffer = new byte[64 * 1024];
        BufferedOutputStream bout = new BufferedOutputStream(out, 64 * 1024);
        int read;
        while((read = in.read(buffer)) != -1){
            bout.write(buffer, 0, read);
            // Bump progress per chunk, not just per file: a multi-MB file copying slowly on wedged-ish
            // flash must not look "stalled" to the watchdog while it is in fact still moving.
            sLastProgressMs = android.os.SystemClock.elapsedRealtime();
        }
        bout.flush();
    }

    /**
     * True iff a full asset export has EVER completed for this install (the version file is only
     * written after every asset copied successfully). LaunchActivity uses this to decide whether the
     * game can safely start: stale data from an older version is fine, but with NO complete export the
     * native side is guaranteed to abort with "cannot find default font definitions".
     */
    public static boolean hasCompletedExport(Activity activity) {
        File dir = activity.getExternalFilesDir(null);
        return dir != null && new File(dir, appversioncodefile).exists();
    }

    /** Free space in MB on the export target, or -1 if external storage is unavailable. */
    public static long getFreeSpaceMb(Activity activity) {
        File dir = activity.getExternalFilesDir(null);
        if(dir == null) return -1;
        android.os.StatFs fs = new android.os.StatFs(dir.getPath());
        return ((long) fs.getBlockSize() * fs.getAvailableBlocks()) / (1024 * 1024);
    }

    private Integer getVersionCodeOfCurrentApp()
    {
        return BuildConfig.VERSION_CODE;
    }

    private Integer getVersionCodeOfLastExportedAssets(Activity activity)
    {
        File f = new File(activity.getExternalFilesDir(null), appversioncodefile);
        if(!f.exists()) return null;

        try {
            FileInputStream stream = new FileInputStream(f);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line = reader.readLine();
            reader.close();
            return Integer.parseInt(line);
        } catch (IOException e) {
            return null;
        }
    }

    private void trySetVersionCodeOfLastExportedAssets(Activity activity, Integer version) {
        File f = new File(activity.getExternalFilesDir(null), appversioncodefile);
        try {
            FileOutputStream stream = new FileOutputStream(f);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(version.toString());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
