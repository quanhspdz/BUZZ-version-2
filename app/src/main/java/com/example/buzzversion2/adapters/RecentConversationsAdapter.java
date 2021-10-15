package com.example.buzzversion2.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Base64;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.buzzversion2.R;
import com.example.buzzversion2.databinding.ItemContainerRecentConversationBinding;
import com.example.buzzversion2.listeners.ConversationListener;
import com.example.buzzversion2.models.ChatMessage;
import com.example.buzzversion2.models.User;
import com.example.buzzversion2.utilities.Constants;
import com.example.buzzversion2.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;

public class RecentConversationsAdapter extends RecyclerView.Adapter<RecentConversationsAdapter.ConversationViewHolder> {

    private final List<ChatMessage> chatMessages;
    private final ConversationListener conversationListener;
    private Context context;
    private PreferenceManager preferenceManager;

    public RecentConversationsAdapter(List<ChatMessage> chatMessages, ConversationListener conversationListener, Context context) {
        this.chatMessages = chatMessages;
        this.conversationListener = conversationListener;
        this.context = context;
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ConversationViewHolder(
                ItemContainerRecentConversationBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                )
        );
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        holder.setData(chatMessages.get(position));
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }


    class ConversationViewHolder extends RecyclerView.ViewHolder {

        ItemContainerRecentConversationBinding binding;

        ConversationViewHolder(ItemContainerRecentConversationBinding itemContainerRecentConversationBinding) {
            super(itemContainerRecentConversationBinding.getRoot());
            binding = itemContainerRecentConversationBinding;
        }

        void setData(ChatMessage chatMessage) {
            preferenceManager = new PreferenceManager(context);
            binding.imageProfile.setImageBitmap(getConversationImage(chatMessage.conversationImage));
            binding.textName.setText(chatMessage.conversationName);
            binding.textRecentMessage.setText(chatMessage.message);
            if (!chatMessage.whoSend.equals(preferenceManager.getString(Constants.KEY_USER_ID))) {
                if (chatMessage.seenStatus == 0) {
                    binding.textRecentMessage.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD_ITALIC));
                } else {
                    binding.textRecentMessage.setTypeface(Typeface.DEFAULT);
                }
            }
            binding.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    User user = new User();
                    user.id = chatMessage.conversationId;
                    user.name = chatMessage.conversationName;
                    user.image = chatMessage.conversationImage;
                    conversationListener.onConversationClicked(user);

                    if (!chatMessage.whoSend.equals(preferenceManager.getString(Constants.KEY_USER_ID))) {
                        FirebaseFirestore database = FirebaseFirestore.getInstance();
                        DocumentReference documentReference =
                                database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(chatMessage.conversionId);
                        documentReference.update(
                                Constants.KEY_SEEN_STATUS, 1
                        );
                    }
                }
            });
        }
    }

    private Bitmap getConversationImage(String encodedImage) {
        byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
