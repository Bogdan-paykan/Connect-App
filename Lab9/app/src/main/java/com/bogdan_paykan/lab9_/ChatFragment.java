package com.bogdan_paykan.lab9_;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;



    public class ChatFragment extends Fragment {

        private static final String TAG = "ChatFragment";
        private static final String PROFILE_DATA_FILE_NAME = "profile_data.txt";
        private static final String CHAT_HISTORY_FILE_NAME = "chat_history.txt";

        private TextView textViewSenderEmail;
        private EditText editTextRecipientEmail;
        private EditText editTextGroupNumber;
        private EditText editTextMessage;
        private Button buttonSend;
        private Button buttonShowHistory;

        private String senderEmail;

        public ChatFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_chat, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            textViewSenderEmail = view.findViewById(R.id.textViewSenderEmail);
            editTextRecipientEmail = view.findViewById(R.id.editTextRecipientEmail);
            editTextGroupNumber = view.findViewById(R.id.editTextGroupNumber);
            editTextMessage = view.findViewById(R.id.editTextMessage);
            buttonSend = view.findViewById(R.id.buttonSend);
            buttonShowHistory = view.findViewById(R.id.buttonShowHistory);

            loadSenderEmail();
            textViewSenderEmail.setText("Моя пошта: " + (senderEmail != null ? senderEmail : "Не знайдено"));

            buttonSend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String recipientEmail = editTextRecipientEmail.getText().toString().trim();
                    String groupNumber = editTextGroupNumber.getText().toString().trim();
                    String message = editTextMessage.getText().toString().trim();

                    String displayedSenderEmail = (senderEmail != null && !senderEmail.isEmpty()) ? senderEmail : "Не вказано";

                    if (recipientEmail.isEmpty()) {
                        Toast.makeText(getContext(), "Будь ласка, введіть пошту отримувача", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (groupNumber.isEmpty()) {
                        Toast.makeText(getContext(), "Будь ласка, введіть номер групи", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (groupNumber.length() > 7) {
                        Toast.makeText(getContext(), "Номер групи занадто довгий (макс. 7 символів)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (message.isEmpty()) {
                        Toast.makeText(getContext(), "Будь ласка, введіть повідомлення", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (senderEmail == null || senderEmail.isEmpty()) {
                        Toast.makeText(getContext(), "Ваша пошта не завантажена. Повідомлення буде надіслано без неї.", Toast.LENGTH_LONG).show();
                    }

                    // Keep the Toast confirmation as requested by the user
                    String toastMessage = "Надіслано:\n" +
                            "Пошта: від " + displayedSenderEmail + " кому " + recipientEmail + "\n" +
                            "№ групи: " + groupNumber + "\n" +
                            "Повідомлення: " + message;
                    Toast.makeText(getContext(), toastMessage, Toast.LENGTH_LONG).show();


                    saveMessageToFile(displayedSenderEmail, recipientEmail, groupNumber, message);
                }
            });

            buttonShowHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showHistoryFile();
                }
            });
        }

        private void loadSenderEmail() {
            if (getContext() == null) {
                Log.e(TAG, "Context is null in loadSenderEmail");
                senderEmail = null;
                return;
            }

            File dataFile = new File(getContext().getFilesDir(), PROFILE_DATA_FILE_NAME);
            if (!dataFile.exists()) {
                Log.i(TAG, "Profile data file does not exist: " + PROFILE_DATA_FILE_NAME + ". Cannot load sender email.");
                senderEmail = null;
                return;
            }

            try (FileInputStream fis = getContext().openFileInput(PROFILE_DATA_FILE_NAME);
                 InputStreamReader isr = new InputStreamReader(fis);
                 BufferedReader br = new BufferedReader(isr)) {

                br.readLine();
                br.readLine();
                String emailLine = br.readLine();

                if (emailLine != null) {
                    String trimmedEmail = emailLine.trim();
                    senderEmail = !trimmedEmail.isEmpty() ? trimmedEmail : null;
                    if (senderEmail != null) {
                        Log.d(TAG, "Sender email loaded: " + senderEmail);
                    } else {
                        Log.i(TAG, "Email line found but is empty/whitespace after trim.");
                    }
                } else {
                    Log.i(TAG, "Email line (3rd line) not found in profile file.");
                    senderEmail = null;
                }

            } catch (IOException e) {
                Log.e(TAG, "Error loading sender email from file: " + PROFILE_DATA_FILE_NAME, e);
                senderEmail = null;
            }
        }

        private void saveMessageToFile(String sender, String recipient, String group, String message) {
            if (getContext() == null) {
                Log.e(TAG, "Context is null in saveMessageToFile");
                return;
            }


            File directory = getContext().getExternalFilesDir(null);
            if (directory == null) {
                Log.e(TAG, "External files directory is null. Cannot save message.");
                Toast.makeText(getContext(), "Не вдалося отримати папку для збереження!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    Log.e(TAG, "Failed to create external files directory: " + directory.getAbsolutePath());
                    Toast.makeText(getContext(), "Не вдалося створити папку для збереження!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            File file = new File(directory, CHAT_HISTORY_FILE_NAME);

            try (FileOutputStream fos = new FileOutputStream(file, true);
                 OutputStreamWriter osw = new OutputStreamWriter(fos)) {

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                String timestamp = sdf.format(new Date());

                osw.write("--- Повідомлення ---\n");
                osw.write("Час: " + timestamp + "\n");
                osw.write("Від: " + sender + "\n");
                osw.write("Кому: " + recipient + "\n");
                osw.write("Група: " + group + "\n");
                osw.write("Текст: " + message + "\n");
                osw.write("--------------------\n\n");

                Log.d(TAG, "Message saved to history file at " + file.getAbsolutePath());

            } catch (IOException e) {
                Log.e(TAG, "Error saving message to history file: " + file.getAbsolutePath(), e);
                Toast.makeText(getContext(), "Помилка збереження історії!", Toast.LENGTH_SHORT).show();
            }
        }

        private void showHistoryFile() {
            if (getContext() == null) {
                Log.e(TAG, "Context is null in showHistoryFile");
                return;
            }

            File directory = getContext().getExternalFilesDir(null);
            if (directory == null) {
                Toast.makeText(getContext(), "Папка історії недоступна.", Toast.LENGTH_SHORT).show();
                return;
            }
            File historyFile = new File(directory, CHAT_HISTORY_FILE_NAME);

            if (!historyFile.exists() || historyFile.length() == 0) {
                Toast.makeText(getContext(), "Історія повідомлень порожня.", Toast.LENGTH_SHORT).show();
                return;
            }

            String filePath = historyFile.getAbsolutePath(); // Отримуємо повний шлях

            new AlertDialog.Builder(getContext())
                    .setTitle("Історія повідомлень")
                    .setMessage("Файл історії збережено за шляхом:\n" + filePath + "\n\nБажаєте відкрити його?")
                    .setPositiveButton("Так", (dialog, which) -> {
                        try {

                            Uri fileUri = FileProvider.getUriForFile(
                                    getContext(),
                                    getContext().getPackageName() + ".fileprovider",
                                    historyFile
                            );

                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(fileUri, "*/*");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                                startActivity(intent);
                            } else {
                                Toast.makeText(getContext(), "Не знайдено додатка для відкриття файлу.", Toast.LENGTH_SHORT).show();
                            }

                        } catch (IllegalArgumentException e) {
                            Log.e(TAG, "Error getting FileProvider URI or starting intent", e);
                            Toast.makeText(getContext(), "Помилка при спробі відкрити файл.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Ні", (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .setIcon(android.R.drawable.ic_menu_save)
                    .show();
        }
    }