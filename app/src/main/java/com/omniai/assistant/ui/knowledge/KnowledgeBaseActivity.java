package com.omniai.assistant.ui.knowledge;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.omniai.assistant.R;
import com.omniai.assistant.credits.CreditsManager;
import com.omniai.assistant.inference.VisionInferenceEngine;
import com.omniai.assistant.knowledge.KnowledgeBaseManager;
import com.omniai.assistant.model.KnowledgeBase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class KnowledgeBaseActivity extends AppCompatActivity {

    private static final String TAG = "KnowledgeBase";

    private static final int PICK_DOCUMENT = 5001;
    private static final int PICK_IMAGE = 5002;

    private RecyclerView kbList;
    private View fabCreate;
    private KnowledgeBaseAdapter adapter;
    private KnowledgeBaseManager kbManager;
    private VisionInferenceEngine visionEngine;
    private EditText searchInput;

    private List<KnowledgeBase> allKnowledgeBases = new ArrayList<>();
    private String currentKbIdForImport;
    private AlertDialog ocrProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_knowledge_base);

        kbManager = KnowledgeBaseManager.getInstance(this);
        visionEngine = VisionInferenceEngine.getInstance();

        kbList = findViewById(R.id.rv_kb_list);
        fabCreate = findViewById(R.id.fab_create);
        searchInput = findViewById(R.id.et_search);

        adapter = new KnowledgeBaseAdapter(new ArrayList<>(), new KnowledgeBaseAdapter.OnKbActionListener() {
            @Override
            public void onClick(KnowledgeBase kb) {
                openKbDetail(kb);
            }

            @Override
            public void onDelete(KnowledgeBase kb) {
                confirmDeleteKb(kb);
            }
        });
        kbList.setLayoutManager(new LinearLayoutManager(this));
        kbList.setAdapter(adapter);

        fabCreate.setOnClickListener(v -> showCreateDialog());

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            searchKnowledgeBases();
            return true;
        });

        loadKnowledgeBases();
    }

    private void loadKnowledgeBases() {
        allKnowledgeBases = kbManager.getAllKnowledgeBases();
        adapter.updateData(allKnowledgeBases);
    }

    private void searchKnowledgeBases() {
        String query = searchInput.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            adapter.updateData(allKnowledgeBases);
            return;
        }

        List<KnowledgeBase> results = kbManager.search(query);
        adapter.updateData(results);
    }

    private void showCreateDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_kb, null);
        EditText nameInput = dialogView.findViewById(R.id.input_kb_name);
        EditText descInput = dialogView.findViewById(R.id.input_kb_description);

        new AlertDialog.Builder(this)
                .setTitle(R.string.create_knowledge_base)
                .setView(dialogView)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String description = descInput.getText().toString().trim();

                    if (TextUtils.isEmpty(name)) {
                        Snackbar.make(findViewById(android.R.id.content), R.string.error_kb_name_empty, Snackbar.LENGTH_SHORT).show();
                        return;
                    }

                    kbManager.createKnowledgeBase(name, description, new KnowledgeBaseManager.KbCallback() {
                        @Override
                        public void onSuccess(KnowledgeBase kb) {
                            runOnUiThread(() -> {
                                allKnowledgeBases.add(kb);
                                adapter.updateData(allKnowledgeBases);
                                Snackbar.make(findViewById(android.R.id.content), R.string.kb_created, Snackbar.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openKbDetail(KnowledgeBase kb) {
        currentKbIdForImport = kb.getId();

        View detailView = getLayoutInflater().inflate(R.layout.dialog_kb_detail, null);
        TextView nameView = detailView.findViewById(R.id.tv_kb_name);
        TextView docCountView = detailView.findViewById(R.id.tv_doc_count);
        TextView sizeView = detailView.findViewById(R.id.tv_kb_size);

        nameView.setText(kb.getName());
        docCountView.setText(getString(R.string.doc_count_format, kb.getDocCount()));
        sizeView.setText(formatSize(kb.getSize()));

        new AlertDialog.Builder(this)
                .setTitle(kb.getName())
                .setView(detailView)
                .setPositiveButton(R.string.import_document, (dialog, which) -> {
                    pickDocument();
                })
                .setNeutralButton(R.string.import_image, (dialog, which) -> {
                    pickImage();
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void pickDocument() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "text/plain", "text/csv", "application/json", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, PICK_DOCUMENT);
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    private void confirmDeleteKb(KnowledgeBase kb) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_kb_title)
                .setMessage(getString(R.string.delete_kb_message, kb.getName()))
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    kbManager.deleteKnowledgeBase(kb.getId(), new KnowledgeBaseManager.KbCallback() {
                        @Override
                        public void onSuccess(KnowledgeBase deleted) {
                            runOnUiThread(() -> {
                                allKnowledgeBases.remove(kb);
                                adapter.updateData(allKnowledgeBases);
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private boolean isValidImageMimeType(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        if (mimeType == null) return false;
        return mimeType.startsWith("image/");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null || currentKbIdForImport == null) {
            return;
        }

        if (requestCode == PICK_DOCUMENT) {
            kbManager.importDocument(currentKbIdForImport, uri, new KnowledgeBaseManager.KbCallback() {
                @Override
                public void onSuccess(KnowledgeBase kb) {
                    runOnUiThread(() -> {
                        loadKnowledgeBases();
                        Snackbar.make(findViewById(android.R.id.content), R.string.document_imported, Snackbar.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show());
                }
            });
        } else if (requestCode == PICK_IMAGE) {
            if (!isValidImageMimeType(uri)) {
                Snackbar.make(findViewById(android.R.id.content), getString(R.string.error_invalid_image), Snackbar.LENGTH_SHORT).show();
                return;
            }
            processImageWithOcr(uri);
        }
    }

    private void processImageWithOcr(Uri imageUri) {
        showOcrProgressDialog();

        File imageFile = copyUriToTempFile(imageUri);
        if (imageFile == null) {
            dismissOcrProgressDialog();
            Snackbar.make(findViewById(android.R.id.content),
                    getString(R.string.error_image_read_failed), Snackbar.LENGTH_SHORT).show();
            return;
        }

        String imagePath = imageFile.getAbsolutePath();

        visionEngine.imageOcr(imagePath, new VisionInferenceEngine.OcrCallback() {
            @Override
            public void onSuccess(String text) {
                runOnUiThread(() -> {
                    dismissOcrProgressDialog();

                    if (TextUtils.isEmpty(text)) {
                        Snackbar.make(findViewById(android.R.id.content),
                                getString(R.string.ocr_no_text_extracted), Snackbar.LENGTH_SHORT).show();
                        return;
                    }

                    String documentTitle = getImageFileName(imageUri);
                    importOcrTextToKnowledgeBase(documentTitle, text);

                    imageFile.delete();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    dismissOcrProgressDialog();
                    Snackbar.make(findViewById(android.R.id.content),
                            getString(R.string.ocr_error, error), Snackbar.LENGTH_SHORT).show();
                    imageFile.delete();
                });
            }
        });
    }

    private void importOcrTextToKnowledgeBase(String title, String ocrText) {
        if (currentKbIdForImport == null) {
            Snackbar.make(findViewById(android.R.id.content),
                    getString(R.string.error_kb_not_selected), Snackbar.LENGTH_SHORT).show();
            return;
        }

        String content = title + "\n\n" + ocrText;

        kbManager.importText(currentKbIdForImport, title, content, new KnowledgeBaseManager.KbCallback() {
            @Override
            public void onSuccess(KnowledgeBase kb) {
                runOnUiThread(() -> {
                    loadKnowledgeBases();
                    Snackbar.make(findViewById(android.R.id.content),
                            getString(R.string.image_imported_with_ocr), Snackbar.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show());
            }
        });
    }

    private File copyUriToTempFile(Uri uri) {
        try {
            String fileName = getImageFileName(uri);
            File tempDir = new File(getCacheDir(), "ocr_temp");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            File tempFile = new File(tempDir, fileName);

            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }

            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            inputStream.close();

            return tempFile;
        } catch (Exception e) {
            return null;
        }
    }

    private String getImageFileName(Uri uri) {
        String fileName = "image_" + System.currentTimeMillis();
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get image file name from URI", e);
        }
        return fileName;
    }

    private void showOcrProgressDialog() {
        if (ocrProgressDialog == null) {
            ocrProgressDialog = new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.ocr_processing))
                    .setCancelable(false)
                    .create();
        }
        ocrProgressDialog.show();
    }

    private void dismissOcrProgressDialog() {
        if (ocrProgressDialog != null && ocrProgressDialog.isShowing()) {
            ocrProgressDialog.dismiss();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadKnowledgeBases();
    }

    private String formatSize(long size) {
        if (size <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
