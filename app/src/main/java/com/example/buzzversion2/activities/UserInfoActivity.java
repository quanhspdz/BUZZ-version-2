package com.example.buzzversion2.activities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;

import com.example.buzzversion2.R;
import com.example.buzzversion2.databinding.ActivityUserInfoBinding;
import com.example.buzzversion2.models.User;
import com.example.buzzversion2.utilities.Constants;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class UserInfoActivity extends BaseActivity {

    ActivityUserInfoBinding binding;
    FirebaseFirestore database;
    String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserInfoBinding.inflate(getLayoutInflater());
        database = FirebaseFirestore.getInstance();
        setContentView(binding.getRoot());
        setListener();
        setUserDetail();
    }

    private void setUserDetail() {
        loading(false);
        Intent intent = getIntent();
        userId = intent.getStringExtra(Constants.KEY_USER_ID);
        if (userId != null) {
            database.collection(Constants.KEY_COLLECTION_USER)
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if (task.isSuccessful() && task.getResult() != null) {
                                for (QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()) {
                                    if (userId.equals(queryDocumentSnapshot.getId())) {
                                        loading(false);
                                        binding.imageProfile.setImageBitmap(
                                                getUserImage(queryDocumentSnapshot.getString(Constants.KEY_IMAGE)));
                                        binding.textName.setText(queryDocumentSnapshot.getString(Constants.KEY_NAME));
                                        binding.textEmail.setText(queryDocumentSnapshot.getString(Constants.KEY_EMAIL));
                                        break;
                                    }
                                }
                            }
                        }
                    });
        }
    }

    private Bitmap getUserImage(String encodedImage) {
        byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private void setListener() {
        binding.imageBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
                finish();
            }
        });
    }

    private void loading(Boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }
}