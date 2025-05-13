package com.bogdan_paykan.lab9_;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap; // Для збереження
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns; // Для перевірки email
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap; // Для отримання розширення файлу
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

// Імпорти для CanHub/Android-Image-Cropper (переконайтесь, що залежність додана в build.gradle.kts)
import com.canhub.cropper.CropImage;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream; // Для копіювання
import java.io.InputStreamReader;
import java.io.OutputStream; // Для копіювання
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ContactsFragment extends Fragment {

    private static final String TAG = "ContactsFragment";
    private static final String PROFILE_DATA_FILE_NAME = "profile_data.txt";
    private static final String CUSTOM_CONTACTS_FILE_NAME = "custom_contacts.json";
    private static final String CHAT_HISTORY_FILE_NAME = "chat_history.txt";
    private static final String CONTACT_IMAGES_DIR = "contact_images"; // Директорія для збережених зображень

    private ListView contactsListView;
    private CustomContactAdapter contactsAdapter;
    private List<DisplayContact> allContactsList;
    private ImageButton addContactButton;
    private ImageButton deleteContactButton;

    private boolean isInDeleteMode = false;
    private Set<String> contactsToDeleteIds = new HashSet<>();

    private DisplayContact userProfileContact;

    // Лаунчери для вибору та обрізки зображення
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<CropImageContractOptions> cropImageLauncher;

    private Uri sourceImageUri; // URI обраного зображення перед обрізкою
    private Uri finalImageUri;  // URI після обрізки (або копіювання в локальне сховище)
    private ImageView dialogIconPreviewGlobal; // Для оновлення прев'ю в діалозі

    // --- Клас DisplayContact (без змін) ---
    private static class DisplayContact {
        String id;
        String firstName;
        String lastName;
        @Nullable String group;
        @Nullable String email;
        @Nullable String imageUriString; // Зберігаємо URI як рядок
        boolean isUserProfile;

        public DisplayContact() {
            this.isUserProfile = false;
        }

        public DisplayContact(String firstName, String lastName, @Nullable String group, @Nullable String email) {
            this.id = UUID.randomUUID().toString();
            this.firstName = firstName;
            this.lastName = lastName;
            this.group = group;
            this.email = email;
            this.isUserProfile = false;
            this.imageUriString = null;
        }

        // Конструктор для профілю користувача
        public DisplayContact(String id, String firstName, String lastName, @Nullable String email, @Nullable String group, @Nullable Uri imageUri) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.group = group;
            this.isUserProfile = true;
            setImageUri(imageUri);
        }

        public String getId() { return id; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        @Nullable public String getGroup() { return group; }
        @Nullable public String getEmail() { return email; }
        public boolean isUserProfile() { return isUserProfile; }

        public String getFullName() {
            String first = (firstName == null || firstName.trim().isEmpty()) ? "" : firstName.trim();
            String last = (lastName == null || lastName.trim().isEmpty()) ? "" : lastName.trim();
            String fullName = (first + " " + last).trim();
            return fullName.isEmpty() ? (isUserProfile ? "Мій Профіль" : "Невідомий Контакт") : fullName;
        }

        @Nullable public Uri getImageUri() {
            if (imageUriString == null) return null;
            try {
                if (imageUriString.startsWith("content://") || imageUriString.startsWith("file://")) {
                    return Uri.parse(imageUriString);
                } else {
                    File file = new File(imageUriString);
                    if (file.exists() && file.isFile()) {
                        return Uri.fromFile(file);
                    } else {
                        Log.w(TAG, "Invalid or non-existent image path/URI string: " + imageUriString);
                        return null;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing image URI string: " + imageUriString, e);
                return null;
            }
        }

        public void setImageUri(@Nullable Uri imageUri) {
            this.imageUriString = (imageUri != null) ? imageUri.toString() : null;
            Log.d(TAG, "Set image URI for contact " + getFullName() + ": " + this.imageUriString);
        }

        public int getPlaceholderImageResId() {
            return R.drawable.ic_contact_placeholder; // Переконайтесь, що у вас є цей ресурс
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DisplayContact that = (DisplayContact) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
    // --- Кінець класу DisplayContact ---

    // --- Клас CustomContactAdapter (без змін) ---
    private class CustomContactAdapter extends ArrayAdapter<DisplayContact> {
        private final LayoutInflater inflater;

        public CustomContactAdapter(@NonNull Context context, List<DisplayContact> contacts) {
            super(context, 0, contacts);
            this.inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            final DisplayContact contact = getItem(position);
            ViewHolder holder;

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.name_item, parent, false);
                holder = new ViewHolder();
                holder.avatarImageView = convertView.findViewById(R.id.contact_avatar_imageview);
                holder.nameTextView = convertView.findViewById(R.id.contact_name_textview);
                holder.detailsButton = convertView.findViewById(R.id.contact_action_button);
                holder.deleteCheckBox = convertView.findViewById(R.id.contact_delete_checkbox);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            if (contact != null) {
                holder.nameTextView.setText(contact.getFullName());
                Uri contactImageUri = contact.getImageUri();
                if (contactImageUri != null) {
                    Log.d(TAG, "Loading image for " + contact.getFullName() + " from URI: " + contactImageUri);
                    try {
                        holder.avatarImageView.setImageURI(contactImageUri);
                        holder.avatarImageView.setBackgroundColor(Color.TRANSPARENT);
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting image URI for contact " + contact.getFullName(), e);
                        holder.avatarImageView.setImageResource(contact.getPlaceholderImageResId());
                    }
                } else {
                    holder.avatarImageView.setImageResource(contact.getPlaceholderImageResId());
                }
                holder.avatarImageView.setAdjustViewBounds(true);

                if (holder.detailsButton != null) {
                    holder.detailsButton.setVisibility(isInDeleteMode ? View.GONE : View.VISIBLE);
                    holder.detailsButton.setOnClickListener(v -> showContactDetailsDialog(contact));
                }

                if (holder.deleteCheckBox != null) {
                    if (isInDeleteMode && !contact.isUserProfile()) {
                        holder.deleteCheckBox.setVisibility(View.VISIBLE);
                        holder.deleteCheckBox.setChecked(contactsToDeleteIds.contains(contact.getId()));
                        holder.deleteCheckBox.setOnCheckedChangeListener(null);
                        holder.deleteCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            if (isChecked) {
                                contactsToDeleteIds.add(contact.getId());
                            } else {
                                contactsToDeleteIds.remove(contact.getId());
                            }
                        });
                    } else {
                        holder.deleteCheckBox.setVisibility(View.GONE);
                        holder.deleteCheckBox.setChecked(false);
                        holder.deleteCheckBox.setOnCheckedChangeListener(null);
                    }
                }
            } else {
                holder.nameTextView.setText("");
                holder.avatarImageView.setImageResource(R.drawable.ic_contact_placeholder);
                holder.detailsButton.setVisibility(View.GONE);
                holder.deleteCheckBox.setVisibility(View.GONE);
            }
            return convertView;
        }

        private class ViewHolder {
            ImageView avatarImageView;
            TextView nameTextView;
            Button detailsButton;
            CheckBox deleteCheckBox;
        }

        // Цей метод більше не потрібен, оновлення відбувається через notifyDataSetChanged()
        // public void setDeleteMode(boolean enabled) { }
    }
    // --- Кінець класу CustomContactAdapter ---

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ініціалізація лаунчера для вибору зображення
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        sourceImageUri = result.getData().getData();
                        Log.d(TAG, "Image picked: " + sourceImageUri);
                        startCrop(sourceImageUri); // Запускаємо обрізку
                    } else {
                        Toast.makeText(getContext(), "Вибір зображення скасовано", Toast.LENGTH_SHORT).show();
                    }
                });

        // Ініціалізація лаунчера для отримання результату обрізки
        cropImageLauncher = registerForActivityResult(new CropImageContract(), result -> {
            if (result.isSuccessful()) {
                Uri croppedUri = result.getUriContent();
                Log.d(TAG, "Image cropped successfully: " + croppedUri);
                // Копіюємо обрізане зображення у внутрішнє сховище
                finalImageUri = copyUriToInternalStorage(croppedUri, "cropped_" + System.currentTimeMillis());
                if (finalImageUri != null) {
                    Log.d(TAG, "Image copied to internal storage: " + finalImageUri);
                    if (dialogIconPreviewGlobal != null) {
                        dialogIconPreviewGlobal.setImageURI(finalImageUri);
                    }
                } else {
                    Log.w(TAG, "Failed to copy cropped image to internal storage, using temporary URI.");
                    finalImageUri = croppedUri;
                    if (dialogIconPreviewGlobal != null && finalImageUri != null) {
                        dialogIconPreviewGlobal.setImageURI(finalImageUri);
                    }
                    Toast.makeText(getContext(), "Помилка збереження зображення", Toast.LENGTH_SHORT).show();
                }
            } else {
                Exception error = result.getError();
                Log.e(TAG, "Image cropping failed", error);
                Toast.makeText(getContext(), "Помилка обрізки: " + (error != null ? error.getMessage() : "Невідома помилка"), Toast.LENGTH_SHORT).show();
                finalImageUri = null;
            }
        });
    }

    // Метод для запуску активності обрізки
    private void startCrop(Uri sourceUri) {
        if (sourceUri == null) {
            Toast.makeText(getContext(), "Немає зображення для обрізки", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Starting crop for URI: " + sourceUri);
        CropImageOptions options = new CropImageOptions();
        options.guidelines = CropImageView.Guidelines.ON;
        options.aspectRatioX = 1;
        options.aspectRatioY = 1;
        options.fixAspectRatio = true;
        options.outputCompressFormat = Bitmap.CompressFormat.JPEG;
        options.outputCompressQuality = 80;
        CropImageContractOptions contractOptions = new CropImageContractOptions(sourceUri, options);
        cropImageLauncher.launch(contractOptions);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        contactsListView = view.findViewById(R.id.contacts_list_view_in_fragment);
        addContactButton = view.findViewById(R.id.button_add_contact);
        deleteContactButton = view.findViewById(R.id.button_delete_contact);
        allContactsList = new ArrayList<>();

        if (getContext() != null) {
            contactsAdapter = new CustomContactAdapter(requireContext(), allContactsList);
            contactsListView.setAdapter(contactsAdapter);
            loadUserProfileData();
            loadContactsFromFile();
            updateAdapterData();
            addContactButton.setOnClickListener(v -> showAddContactDialog());
            deleteContactButton.setOnClickListener(v -> toggleDeleteMode());
        }

        getParentFragmentManager().setFragmentResultListener("profileUpdated", this, (requestKey, bundle) -> {
            Log.d(TAG, "Received profileUpdated event");
            loadUserProfileData();
            updateAdapterData();
        });
    }

    // Метод завантаження даних профілю (без змін)
    private void loadUserProfileData() {
        Context context = getContext();
        if (context == null) return;
        String firstName = null, lastName = null, email = null, group = "Моя Група", imagePath = null;
        Uri imageUri = null;
        final String PROFILE_ID = "USER_PROFILE_ID_001";

        File dataFile = new File(context.getFilesDir(), PROFILE_DATA_FILE_NAME);
        if (dataFile.exists()) {
            try (FileInputStream fis = context.openFileInput(PROFILE_DATA_FILE_NAME);
                 InputStreamReader isr = new InputStreamReader(fis);
                 BufferedReader br = new BufferedReader(isr)) {
                firstName = br.readLine();
                lastName = br.readLine();
                email = br.readLine();
                imagePath = br.readLine();
                if (imagePath != null && !imagePath.trim().isEmpty()) {
                    try {
                        imageUri = Uri.parse(imagePath);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing profile image URI from file: " + imagePath, e);
                        imageUri = null;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error loading profile data", e);
            }
        }

        if (userProfileContact == null) {
            userProfileContact = new DisplayContact(PROFILE_ID, firstName, lastName, email, group, imageUri);
            userProfileContact.isUserProfile = true;
        } else {
            userProfileContact.firstName = firstName;
            userProfileContact.lastName = lastName;
            userProfileContact.email = email;
            userProfileContact.group = group;
            userProfileContact.setImageUri(imageUri);
        }
        Log.d(TAG, "Profile data loaded: " + userProfileContact.getFullName() + ", Image URI: " + userProfileContact.imageUriString);
    }

    // Метод завантаження контактів з файлу (без змін)
    private void loadContactsFromFile() {
        Context context = getContext();
        if (context == null) return;
        File file = new File(context.getFilesDir(), CUSTOM_CONTACTS_FILE_NAME);
        if (!file.exists()) {
            Log.i(TAG, "Custom contacts file does not exist.");
            return;
        }
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(file)) {
            Type contactListType = new TypeToken<ArrayList<DisplayContact>>(){}.getType();
            List<DisplayContact> loadedContacts = gson.fromJson(reader, contactListType);
            allContactsList.removeIf(contact -> !contact.isUserProfile());
            if (loadedContacts != null) {
                allContactsList.addAll(loadedContacts);
                Log.i(TAG, "Loaded " + loadedContacts.size() + " custom contacts.");
            } else {
                Log.i(TAG, "Loaded custom contacts list is null.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading or parsing custom contacts from " + CUSTOM_CONTACTS_FILE_NAME, e);
        }
    }

    // Метод оновлення даних в адаптері (без змін)
    private void updateAdapterData() {
        if (contactsAdapter == null) {
            Log.w(TAG, "Adapter is null, cannot update data.");
            return;
        }
        allContactsList.removeIf(DisplayContact::isUserProfile);
        if(userProfileContact != null) {
            allContactsList.add(0, userProfileContact);
        }
        Log.d(TAG, "Updating adapter with " + allContactsList.size() + " total contacts.");
        contactsAdapter.notifyDataSetChanged();
    }

    // Метод збереження контактів у файл (без змін)
    private void saveContactsToFile() {
        Context context = getContext();
        if (context == null) return;
        Gson gson = new Gson();
        List<DisplayContact> contactsToSave = allContactsList.stream()
                .filter(contact -> !contact.isUserProfile())
                .collect(Collectors.toList());
        Log.d(TAG, "Saving " + contactsToSave.size() + " custom contacts to file.");
        try (FileWriter writer = new FileWriter(new File(context.getFilesDir(), CUSTOM_CONTACTS_FILE_NAME))) {
            gson.toJson(contactsToSave, writer);
        } catch (IOException e) {
            Log.e(TAG, "Error saving custom contacts", e);
            Toast.makeText(context, "Помилка збереження контактів", Toast.LENGTH_SHORT).show();
        }
    }

    // Метод показу діалогу додавання контакту (без змін)
    private void showAddContactDialog() {
        Context context = getContext();
        if (context == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_contact, null);
        final EditText firstNameInput = dialogView.findViewById(R.id.edit_text_first_name);
        final EditText lastNameInput = dialogView.findViewById(R.id.edit_text_last_name);
        final EditText groupInput = dialogView.findViewById(R.id.edit_text_group);
        final EditText emailInput = dialogView.findViewById(R.id.edit_text_email);
        dialogIconPreviewGlobal = dialogView.findViewById(R.id.image_view_contact_icon_preview);
        final Button selectIconButton = dialogView.findViewById(R.id.button_select_contact_icon);
        finalImageUri = null;
        if (dialogIconPreviewGlobal != null) {
            dialogIconPreviewGlobal.setImageResource(R.drawable.ic_contact_placeholder);
        }
        if (selectIconButton != null) {
            selectIconButton.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                pickImageLauncher.launch(intent);
            });
        }
        builder.setView(dialogView)
                .setTitle("Додати новий контакт")
                .setPositiveButton("Підтвердити", (dialog, id) -> {
                    String firstName = firstNameInput.getText().toString().trim();
                    String lastName = lastNameInput.getText().toString().trim();
                    String group = groupInput.getText().toString().trim();
                    String email = emailInput.getText().toString().trim();
                    if (TextUtils.isEmpty(firstName) && TextUtils.isEmpty(lastName)) {
                        Toast.makeText(context, "Введіть хоча б ім'я або прізвище", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        Toast.makeText(context, "Введіть коректну адресу електронної пошти", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DisplayContact newContact = new DisplayContact(firstName, lastName, group.isEmpty() ? null : group, email);
                    if (finalImageUri != null) {
                        newContact.setImageUri(finalImageUri);
                        Log.d(TAG, "Assigning final image URI to new contact: " + finalImageUri);
                    } else {
                        Log.d(TAG, "No final image URI to assign to new contact.");
                    }
                    allContactsList.add(newContact);
                    saveContactsToFile();
                    updateAdapterData();
                    Log.i(TAG, "New contact added: " + newContact.getFullName());
                    Toast.makeText(context, "Контакт '" + newContact.getFullName() + "' додано", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Скасувати", (dialog, id) -> {
                    dialog.cancel();
                });
        builder.create().show();
    }

    // --- ОНОВЛЕНИЙ Метод перемикання режиму видалення ---
    private void toggleDeleteMode() {
        Context context = getContext();
        if (context == null || contactsAdapter == null) return;

        isInDeleteMode = !isInDeleteMode; // Перемикаємо режим

        if (isInDeleteMode) {
            // Вхід в режим видалення
            contactsToDeleteIds.clear(); // Готуємо множину для ID на видалення
            if (deleteContactButton != null) {
                ImageViewCompat.setImageTintList(deleteContactButton, ColorStateList.valueOf(Color.RED)); // Червона кнопка
            }
            Toast.makeText(context, "Оберіть контакти для видалення", Toast.LENGTH_SHORT).show();
        } else {
            // Вихід з режиму видалення
            if (!contactsToDeleteIds.isEmpty()) {
                // Якщо є обрані контакти, видаляємо їх БЕЗ підтвердження
                deleteSelectedContacts();
            } else {
                // Якщо нічого не обрано
                Toast.makeText(context, "Режим видалення скасовано", Toast.LENGTH_SHORT).show();
            }
            // Завершення виходу з режиму
            resetDeleteButtonAppearance(); // Повертаємо колір кнопки
            contactsToDeleteIds.clear(); // Завжди очищаємо сет після виходу
        }

        // Оновлюємо вигляд списку (показати/сховати чекбокси)
        contactsAdapter.notifyDataSetChanged();
    }
    // --- Кінець ОНОВЛЕНОГО методу toggleDeleteMode ---

    // --- ВИДАЛЕНО метод showConfirmDeleteDialog() ---

    // Метод логіки видалення обраних контактів (без змін)
    private void deleteSelectedContacts() {
        if (contactsToDeleteIds.isEmpty()) {
            Log.d(TAG, "deleteSelectedContacts called but no contacts were selected.");
            return;
        }
        Context context = getContext();
        int countBefore = allContactsList.size();
        List<String> removedNames = new ArrayList<>();

        Iterator<DisplayContact> iterator = allContactsList.iterator();
        while (iterator.hasNext()) {
            DisplayContact contact = iterator.next();
            if (!contact.isUserProfile() && contactsToDeleteIds.contains(contact.getId())) {
                removedNames.add(contact.getFullName());
                Uri imageUri = contact.getImageUri();
                if (imageUri != null && "file".equals(imageUri.getScheme())) {
                    File imageFile = new File(imageUri.getPath());
                    if (imageFile.exists() && imageFile.isFile()) {
                        if (imageFile.delete()) {
                            Log.d(TAG, "Deleted associated image file: " + imageUri.getPath());
                        } else {
                            Log.w(TAG, "Failed to delete associated image file: " + imageUri.getPath());
                        }
                    }
                }
                iterator.remove();
            }
        }

        int numDeleted = countBefore - allContactsList.size();

        if (numDeleted > 0) {
            Log.i(TAG, "Deleted " + numDeleted + " contacts: " + String.join(", ", removedNames));
            if (context != null) {
                Toast.makeText(context, "Видалено: " + numDeleted + " контакт(ів)", Toast.LENGTH_SHORT).show();
            }
            saveContactsToFile();
        } else {
            Log.i(TAG, "No contacts were actually removed.");
            if (context != null && !contactsToDeleteIds.isEmpty()) {
                Toast.makeText(context, "Не вдалося видалити обрані контакти.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Метод повернення стандартного вигляду кнопки видалення (без змін)
    private void resetDeleteButtonAppearance() {
        Context context = getContext();
        if (context != null && deleteContactButton != null) {
            int defaultColor = ContextCompat.getColor(context, R.color.secondaryText); // Використовуйте ваш колір
            ImageViewCompat.setImageTintList(deleteContactButton, ColorStateList.valueOf(defaultColor));
        }
    }

    // Метод показу діалогу деталей контакту (без змін)
    private void showContactDetailsDialog(DisplayContact contact) {
        Context context = getContext();
        if (context == null || contact == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Деталі контакту");
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        TextView nameText = new TextView(context);
        nameText.setText("Ім'я: " + contact.getFullName());
        nameText.setTextSize(18f);
        nameText.setPadding(0,0,0, padding/2);
        layout.addView(nameText);
        TextView groupText = new TextView(context);
        groupText.setText("Група: " + (contact.getGroup() != null ? contact.getGroup() : "не вказано"));
        groupText.setTextSize(16f);
        groupText.setPadding(0,0,0, padding/2);
        layout.addView(groupText);
        TextView emailText = new TextView(context);
        emailText.setText("Email: " + (contact.getEmail() != null ? contact.getEmail() : "не вказано"));
        emailText.setTextSize(16f);
        layout.addView(emailText);
        builder.setView(layout);
        if (!contact.isUserProfile()) {
            builder.setPositiveButton("Ваші повідомлення", (dialog, which) -> {
                exportMessagesForContact(contact.getFullName(), contact.getEmail());
            });
        }
        builder.setNegativeButton("Закрити", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    // Метод експорту повідомлень (без змін)
    private void exportMessagesForContact(String contactFullName, @Nullable String contactEmail) {
        Context context = getContext();
        if (context == null || (TextUtils.isEmpty(contactFullName) && TextUtils.isEmpty(contactEmail))) {
            Toast.makeText(context, "Недостатньо даних для фільтрації повідомлень", Toast.LENGTH_SHORT).show();
            return;
        }
        File historyFile = null;
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir != null) {
            historyFile = new File(externalDir, CHAT_HISTORY_FILE_NAME);
        }
        if (historyFile == null || !historyFile.exists()) {
            historyFile = new File(context.getFilesDir(), CHAT_HISTORY_FILE_NAME);
            if (!historyFile.exists()){
                Toast.makeText(context, "Файл історії чату '" + CHAT_HISTORY_FILE_NAME + "' не знайдено.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        Log.d(TAG, "Exporting messages for: Name='" + contactFullName + "', Email='" + contactEmail + "' from file: " + historyFile.getAbsolutePath());
        List<String> filteredMessages = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(historyFile);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            String line;
            String recipientNameIdentifier = "Кому: " + contactFullName;
            String senderNameIdentifier = "Від: " + contactFullName;
            String recipientEmailIdentifier = (!TextUtils.isEmpty(contactEmail)) ? "Кому: " + contactEmail : null;
            String senderEmailIdentifier = (!TextUtils.isEmpty(contactEmail)) ? "Від: " + contactEmail : null;
            List<String> currentMessageBlock = new ArrayList<>();
            boolean inRelevantBlock = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("--- Повідомлення ---") || line.startsWith("--------------------")) {
                    if (inRelevantBlock) {
                        filteredMessages.addAll(currentMessageBlock);
                        filteredMessages.add("");
                    }
                    currentMessageBlock.clear();
                    inRelevantBlock = false;
                    if (!line.startsWith("--- Повідомлення ---") && !line.startsWith("--------------------")) {
                        currentMessageBlock.add(line);
                        if (line.startsWith(recipientNameIdentifier) || line.startsWith(senderNameIdentifier) ||
                                (recipientEmailIdentifier != null && line.startsWith(recipientEmailIdentifier)) ||
                                (senderEmailIdentifier != null && line.startsWith(senderEmailIdentifier))) {
                            inRelevantBlock = true;
                        }
                    }
                } else {
                    currentMessageBlock.add(line);
                    if (!inRelevantBlock) {
                        if (line.startsWith(recipientNameIdentifier) || line.startsWith(senderNameIdentifier) ||
                                (recipientEmailIdentifier != null && line.startsWith(recipientEmailIdentifier)) ||
                                (senderEmailIdentifier != null && line.startsWith(senderEmailIdentifier))) {
                            inRelevantBlock = true;
                        }
                    }
                }
            }
            if (inRelevantBlock && !currentMessageBlock.isEmpty()) {
                filteredMessages.addAll(currentMessageBlock);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading chat history file", e);
            Toast.makeText(context, "Помилка читання історії чату", Toast.LENGTH_SHORT).show();
            return;
        }
        if (filteredMessages.isEmpty()) {
            Toast.makeText(context, "Не знайдено повідомлень для '" + contactFullName + "' " + (contactEmail != null ? "(" + contactEmail + ")" : ""), Toast.LENGTH_SHORT).show();
            return;
        }
        String messagesText = String.join("\n", filteredMessages).trim();
        try {
            File cacheDir = context.getCacheDir();
            File tempDir = new File(cacheDir, "chat_exports");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            String safeFileName = contactFullName.replaceAll("[^a-zA-Z0-9_.-]", "_");
            if (!TextUtils.isEmpty(contactEmail)) {
                safeFileName += "_" + contactEmail.replaceAll("[^a-zA-Z0-9_.-]", "_");
            }
            File tempFile = new File(tempDir, "chat_with_" + safeFileName + ".txt");
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                osw.write(messagesText);
            }
            Log.d(TAG, "Chat history exported to temporary file: " + tempFile.getAbsolutePath());
            Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", tempFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Історія чату з " + contactFullName);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Поділитися історією чату через"));
        } catch (Exception e) {
            Log.e(TAG, "Error exporting/sharing chat history", e);
            Toast.makeText(context, "Помилка експорту історії чату: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Метод копіювання URI у внутрішнє сховище (без змін)
    private Uri copyUriToInternalStorage(Uri sourceUri, String uniqueFileName) {
        Context context = getContext();
        if (context == null || sourceUri == null || uniqueFileName == null) {
            return null;
        }
        ContentResolver resolver = context.getContentResolver();
        String fileExtension = getMimeType(context, sourceUri);
        String destinationFileName = uniqueFileName + (fileExtension != null ? "." + fileExtension : "");
        File imagesDir = new File(context.getFilesDir(), CONTACT_IMAGES_DIR);
        if (!imagesDir.exists()) {
            if (!imagesDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory for contact images: " + imagesDir.getAbsolutePath());
                return null;
            }
        }
        File destinationFile = new File(imagesDir, destinationFileName);
        try (InputStream inputStream = resolver.openInputStream(sourceUri);
             OutputStream outputStream = new FileOutputStream(destinationFile)) {
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for source URI: " + sourceUri);
                return null;
            }
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
            Log.i(TAG, "Successfully copied URI " + sourceUri + " to " + destinationFile.getAbsolutePath());
            return Uri.fromFile(destinationFile);
        } catch (IOException e) {
            Log.e(TAG, "Error copying URI " + sourceUri + " to internal storage", e);
            if (destinationFile.exists()) {
                destinationFile.delete();
            }
            return null;
        } catch (SecurityException se) {
            Log.e(TAG, "Security Exception copying URI " + sourceUri + ". Check permissions.", se);
            return null;
        }
    }

    // Метод отримання MIME типу (без змін)
    private String getMimeType(Context context, Uri uri) {
        String extension;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            final MimeTypeMap mime = MimeTypeMap.getSingleton();
            extension = mime.getExtensionFromMimeType(context.getContentResolver().getType(uri));
        } else {
            extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(new File(uri.getPath())).toString());
        }
        return extension;
    }

}