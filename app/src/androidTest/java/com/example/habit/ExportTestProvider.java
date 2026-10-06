package com.example.habit;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;

/** Standalone test APK provider: private fixture files, never user Downloads or cloud. */
public final class ExportTestProvider extends DocumentsProvider {
    private File directory() {
        File folder = new File(getContext().getCacheDir(), "exports-qa");
        folder.mkdirs();
        return folder;
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor queryRoots(String[] projection) {
        String[] columns = projection != null ? projection : new String[] {Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES, Root.COLUMN_AVAILABLE_BYTES};
        MatrixCursor cursor = new MatrixCursor(columns);
        if (!getContext().getSharedPreferences("qa-control", android.content.Context.MODE_PRIVATE).getBoolean("active", false)) return cursor;
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(Root.COLUMN_ROOT_ID, "qa"); row.add(Root.COLUMN_DOCUMENT_ID, "root"); row.add(Root.COLUMN_TITLE, "Habit QA");
        row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_LOCAL_ONLY); row.add(Root.COLUMN_MIME_TYPES, "application/json"); row.add(Root.COLUMN_AVAILABLE_BYTES, directory().getFreeSpace());
        return cursor;
    }
    private File document(String id) {
        if ("root".equals(id) || !id.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("Invalid fixture id");
        return new File(directory(), id);
    }
    private MatrixCursor cursor(String[] projection) {
        return new MatrixCursor(projection != null ? projection : new String[] {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE});
    }
    private void add(MatrixCursor cursor, String id) {
        boolean root = "root".equals(id);
        File file = root ? directory() : document(id);
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, id); row.add(Document.COLUMN_DISPLAY_NAME, root ? "Habit QA" : id);
        row.add(Document.COLUMN_MIME_TYPE, root ? Document.MIME_TYPE_DIR : "application/json");
        row.add(Document.COLUMN_FLAGS, root ? Document.FLAG_DIR_SUPPORTS_CREATE : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE);
        row.add(Document.COLUMN_SIZE, root ? 0 : file.length());
    }
    @Override public Cursor queryDocument(String id, String[] projection) { MatrixCursor cursor = cursor(projection); add(cursor, id); return cursor; }
    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sort) {
        MatrixCursor cursor = cursor(projection); File[] files = directory().listFiles();
        if (files != null) for (File file : files) add(cursor, file.getName());
        return cursor;
    }
    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException { return ParcelFileDescriptor.open(document(id), ParcelFileDescriptor.parseMode(mode)); }
    @Override public String createDocument(String parent, String mimeType, String displayName) throws FileNotFoundException {
        if (!"root".equals(parent)) throw new FileNotFoundException("Invalid parent");
        String name = "qa-" + System.nanoTime() + ".json";
        try { if (!document(name).createNewFile()) throw new FileNotFoundException("Fixture already exists"); }
        catch (java.io.IOException e) { throw new FileNotFoundException(e.getMessage()); }
        return name;
    }
    @Override public void deleteDocument(String id) throws FileNotFoundException { if (!document(id).delete()) throw new FileNotFoundException("Could not delete fixture"); }
}
