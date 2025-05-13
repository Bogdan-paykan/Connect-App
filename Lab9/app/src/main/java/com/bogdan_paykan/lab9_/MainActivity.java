package com.bogdan_paykan.lab9_;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    private Button profileButtonNav;
    private Button chatButtonNav;
    // webButtonNav не потрібен як змінна, оскільки onClick обробляється в XML
    private Button contactsListButtonNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        profileButtonNav = findViewById(R.id.profile_button_nav);
        chatButtonNav = findViewById(R.id.chat_button_nav);
        contactsListButtonNav = findViewById(R.id.contacts_list_button_nav);

        profileButtonNav.setOnClickListener(v -> {
            loadFragment(new ProfileFragment());
        });

        chatButtonNav.setOnClickListener(v -> {
            // Переконайтеся, що у вас є ChatFragment.java та fragment_chat.xml
            loadFragment(new ChatFragment());
        });

        contactsListButtonNav.setOnClickListener(v -> {
            loadFragment(new ContactsFragment());
        });

        if (savedInstanceState == null) {
            if (!processIncomingIntent(getIntent())) {
                loadFragment(new ProfileFragment());
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processIncomingIntent(intent);
    }

    private boolean processIncomingIntent(Intent intent) {
        if (intent != null && "profile".equals(intent.getStringExtra("navigateTo"))) {
            FragmentManager fm = getSupportFragmentManager();
            if (fm.getBackStackEntryCount() > 0) {
                fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
            loadFragment(new ProfileFragment());
            intent.removeExtra("navigateTo");
            return true;
        }
        return false;
    }

    private void loadFragment(Fragment fragment) {
        String fragmentTag = fragment.getClass().getName();
        FragmentManager fm = getSupportFragmentManager();

        // Опціонально: перевірка, чи фрагмент вже на вершині стеку
        // if (fm.getBackStackEntryCount() > 0) {
        //     FragmentManager.BackStackEntry backStackEntry = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 1);
        //     if (fragmentTag.equals(backStackEntry.getName())) {
        //         return;
        //     }
        // }

        FragmentTransaction transaction = fm.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment, fragmentTag);
        transaction.addToBackStack(fragmentTag);
        transaction.commit();
    }

    // Метод для кнопки "Веб", викликається з XML
    public void goWeb(View view) {
        Intent intent = new Intent(this, WebActivity.class);
        startActivity(intent);
    }
}



