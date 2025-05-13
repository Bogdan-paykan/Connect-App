package com.bogdan_paykan.lab9_;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private TextView firstNameTextView, lastNameTextView, emailTextView;
    private EditText firstNameEditText, lastNameEditText, emailEditText;
    private Button editButton;
    private ImageView profileImageView;
    private ImageButton editImageButton;

    private boolean isInEditMode = false;
    private static final String PROFILE_DATA_FILE_NAME = "profile_data.txt";
    private static final String PROFILE_IMAGE_FILE_NAME = "profile_photo.jpg";
    private Uri currentImageFileUri = null;

    private ActivityResultLauncher<String> imagePickerLauncher;

    private static final String PLACEHOLDER_FIRST_NAME = "Ім'я";
    private static final String PLACEHOLDER_LAST_NAME = "Прізвище";
    private static final String PLACEHOLDER_EMAIL = "Електронна пошта";


    public ProfileFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                selectedImageUri -> {
                    if (selectedImageUri != null) {
                        Log.d(TAG, "Image selected from picker: " + selectedImageUri.toString());
                        File internalImageFile = copyImageToInternalStorage(selectedImageUri, PROFILE_IMAGE_FILE_NAME);
                        if (internalImageFile != null) {
                            currentImageFileUri = Uri.fromFile(internalImageFile);
                            profileImageView.setImageURI(currentImageFileUri);
                            Log.d(TAG, "Image copied to internal storage and URI set: " + currentImageFileUri.toString());
                        } else {
                            Log.e(TAG, "Failed to copy image to internal storage.");
                            Toast.makeText(getContext(), "Не вдалося обробити зображення", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(TAG, "No image selected from picker.");
                    }
                });
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firstNameTextView = view.findViewById(R.id.first_name_text);
        lastNameTextView = view.findViewById(R.id.last_name_text);
        emailTextView = view.findViewById(R.id.email_text);

        firstNameEditText = view.findViewById(R.id.first_name_edit_text);
        lastNameEditText = view.findViewById(R.id.last_name_edit_text);
        emailEditText = view.findViewById(R.id.email_edit_text);

        editButton = view.findViewById(R.id.edit_button);
        profileImageView = view.findViewById(R.id.profile_image);
        editImageButton = view.findViewById(R.id.button_edit_image);

        firstNameTextView.setText(PLACEHOLDER_FIRST_NAME);
        lastNameTextView.setText(PLACEHOLDER_LAST_NAME);
        emailTextView.setText(PLACEHOLDER_EMAIL);

        loadDataFromFile();

        editButton.setOnClickListener(v -> {
            if (isInEditMode) {
                saveProfileChanges();
            } else {
                enterEditMode();
            }
        });

        editImageButton.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });
    }

    private void enterEditMode() {
        firstNameTextView.setVisibility(View.GONE);
        lastNameTextView.setVisibility(View.GONE);
        emailTextView.setVisibility(View.GONE);

        firstNameEditText.setVisibility(View.VISIBLE);
        lastNameEditText.setVisibility(View.VISIBLE);
        emailEditText.setVisibility(View.VISIBLE);
        editImageButton.setVisibility(View.VISIBLE);

        String currentFirstName = firstNameTextView.getText().toString();
        if (PLACEHOLDER_FIRST_NAME.equals(currentFirstName) || currentFirstName.trim().isEmpty()) {
            firstNameEditText.setText("");
        } else {
            firstNameEditText.setText(currentFirstName);
        }

        String currentLastName = lastNameTextView.getText().toString();
        if (PLACEHOLDER_LAST_NAME.equals(currentLastName) || currentLastName.trim().isEmpty()) {
            lastNameEditText.setText("");
        } else {
            lastNameEditText.setText(currentLastName);
        }

        String currentEmail = emailTextView.getText().toString();
        if (PLACEHOLDER_EMAIL.equals(currentEmail) || currentEmail.trim().isEmpty()) {
            emailEditText.setText("");
        } else {
            emailEditText.setText(currentEmail);
        }

        firstNameEditText.requestFocus();

        editButton.setText("Зберегти");
        isInEditMode = true;
    }

    private void saveProfileChanges() {
        String newFirstName = firstNameEditText.getText().toString().trim();
        String newLastName = lastNameEditText.getText().toString().trim();
        String newEmail = emailEditText.getText().toString().trim();

        firstNameTextView.setText(newFirstName.isEmpty() ? PLACEHOLDER_FIRST_NAME : newFirstName);
        lastNameTextView.setText(newLastName.isEmpty() ? PLACEHOLDER_LAST_NAME : newLastName);
        emailTextView.setText(newEmail.isEmpty() ? PLACEHOLDER_EMAIL : newEmail);

        firstNameTextView.setVisibility(View.VISIBLE);
        lastNameTextView.setVisibility(View.VISIBLE);
        emailTextView.setVisibility(View.VISIBLE);

        firstNameEditText.setVisibility(View.GONE);
        lastNameEditText.setVisibility(View.GONE);
        emailEditText.setVisibility(View.GONE);
        editImageButton.setVisibility(View.GONE);

        editButton.setText("Редагувати");

        saveDataToFile(newFirstName, newLastName, newEmail);

        // **НОВЕ: Надіслати результат для оновлення ContactsFragment**
        Bundle resultBundle = new Bundle();
        // resultBundle.putBoolean("updated", true); // Можна додати дані, якщо потрібно
        getParentFragmentManager().setFragmentResult("profileUpdated", resultBundle);
        Log.d(TAG, "Profile update signal sent.");


        isInEditMode = false;
    }

    private File copyImageToInternalStorage(Uri sourceUri, String desiredFileName) {
        if (getContext() == null) {
            Log.e(TAG, "Context is null in copyImageToInternalStorage");
            return null;
        }
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        File destinationFile = new File(getContext().getFilesDir(), desiredFileName);

        try {
            inputStream = getContext().getContentResolver().openInputStream(sourceUri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to get InputStream from source URI: " + sourceUri);
                return null;
            }
            outputStream = new FileOutputStream(destinationFile);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            Log.d(TAG, "Image copied successfully to: " + destinationFile.getAbsolutePath());
            return destinationFile;
        } catch (IOException e) {
            Log.e(TAG, "Error copying image to internal storage. Source: " + sourceUri, e);
            if (destinationFile.exists()) {
                // Спробувати видалити частково записаний файл у разі помилки
                if (!destinationFile.delete()) {
                    Log.w(TAG, "Failed to delete partially written file: " + destinationFile.getAbsolutePath());
                }
            }
            return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams in copyImageToInternalStorage", e);
            }
        }
    }

    private void saveDataToFile(String firstName, String lastName, String email) {
        if (getContext() == null) {
            Log.e(TAG, "Context is null in saveDataToFile");
            return;
        }
        Log.d(TAG, "Attempting to save data. Image URI: " + (currentImageFileUri != null ? currentImageFileUri.toString() : "null"));

        try (FileOutputStream fos = getContext().openFileOutput(PROFILE_DATA_FILE_NAME, Context.MODE_PRIVATE);
             OutputStreamWriter osw = new OutputStreamWriter(fos)) {

            osw.write(firstName + "\n");
            osw.write(lastName + "\n");
            osw.write(email + "\n");
            if (currentImageFileUri != null && "file".equals(currentImageFileUri.getScheme())) {
                osw.write(currentImageFileUri.getPath() + "\n");
                Log.d(TAG, "Saving image file path: " + currentImageFileUri.getPath());
            } else {
                osw.write("\n"); // Записати порожній рядок, якщо URI немає або він не файловий
                Log.d(TAG, "No valid local image file path to save.");
            }

            Toast.makeText(getContext(), "Дані збережено!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error saving data to file: " + PROFILE_DATA_FILE_NAME, e);
            Toast.makeText(getContext(), "Помилка збереження файлу!", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadDataFromFile() {
        if (getContext() == null) {
            Log.e(TAG, "Context is null in loadDataFromFile");
            return;
        }

        File dataFile = new File(getContext().getFilesDir(), PROFILE_DATA_FILE_NAME);
        if (!dataFile.exists()) {
            Log.i(TAG, "Profile data file does not exist: " + PROFILE_DATA_FILE_NAME + ". Using placeholders.");
            profileImageView.setImageResource(R.drawable.bogdan); // Або ваш плейсхолдер
            currentImageFileUri = null;
            // TextViews вже мають placeholder текст з onViewCreated
            return;
        }

        try (FileInputStream fis = getContext().openFileInput(PROFILE_DATA_FILE_NAME);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader br = new BufferedReader(isr)) {

            String firstName = br.readLine();
            String lastName = br.readLine();
            String email = br.readLine();
            String imagePathString = br.readLine();

            if (firstName != null && !firstName.trim().isEmpty()) firstNameTextView.setText(firstName);
            else firstNameTextView.setText(PLACEHOLDER_FIRST_NAME);

            if (lastName != null && !lastName.trim().isEmpty()) lastNameTextView.setText(lastName);
            else lastNameTextView.setText(PLACEHOLDER_LAST_NAME);

            if (email != null && !email.trim().isEmpty()) emailTextView.setText(email);
            else emailTextView.setText(PLACEHOLDER_EMAIL);

            Log.d(TAG, "Loading image path string: '" + (imagePathString != null ? imagePathString : "null") + "'");
            if (imagePathString != null && !imagePathString.trim().isEmpty()) {
                File imageFile = new File(imagePathString.trim());
                if (imageFile.exists() && imageFile.isFile()) {
                    currentImageFileUri = Uri.fromFile(imageFile);
                    profileImageView.setImageURI(currentImageFileUri);
                    Log.i(TAG, "Image successfully loaded from internal path: " + imagePathString);
                } else {
                    Log.w(TAG, "Image file not found or is not a file at path: " + imagePathString + ". Loading default image.");
                    profileImageView.setImageResource(R.drawable.bogdan); // Або ваш плейсхолдер
                    currentImageFileUri = null;
                }
            } else {
                Log.i(TAG, "No image path found in file or path is empty. Loading default image.");
                profileImageView.setImageResource(R.drawable.bogdan); // Або ваш плейсхолдер
                currentImageFileUri = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading data from file: " + PROFILE_DATA_FILE_NAME, e);
            profileImageView.setImageResource(R.drawable.bogdan); // Або ваш плейсхолдер у разі помилки
            currentImageFileUri = null;
        }
    }
}