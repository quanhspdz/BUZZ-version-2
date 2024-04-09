package com.quanhspdz.buzzversion2.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import com.quanhspdz.buzzversion2.R;
import com.quanhspdz.buzzversion2.adapters.ChatAdapter;
import com.quanhspdz.buzzversion2.databinding.ActivityChatBinding;
import com.quanhspdz.buzzversion2.models.ChatMessage;
import com.quanhspdz.buzzversion2.models.User;
import com.quanhspdz.buzzversion2.network.ApiClient;
import com.quanhspdz.buzzversion2.network.ApiService;
import com.quanhspdz.buzzversion2.utilities.Constants;
import com.quanhspdz.buzzversion2.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {

    private ActivityChatBinding binding;
    private User receivedUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversationId = null;
    private Boolean isReceiverAvailable = false;
    private String lastMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        loadReceivedUserDetails();
        setListener();
        init();
        listenerMessages();
        listenAvailabilityOfReceiver();
    }
    private void listenAvailabilityOfReceiver() {
        database.collection(Constants.KEY_COLLECTION_USER)
                .document(receivedUser.id)
                .addSnapshotListener(ChatActivity.this, ((value, error) -> {
                    if (error != null) {
                        return;
                    }
                    if (value != null) {
                        if (value.getLong(Constants.KEY_AVAILABILITY) != null) {
                            int availability = Objects.requireNonNull(
                                    value.getLong(Constants.KEY_AVAILABILITY)
                            ).intValue();
                            isReceiverAvailable = availability == 1;
                        }
                        receivedUser.token = value.getString(Constants.KEY_FCM_TOKEN);
                        if (receivedUser.image == null) {
                            receivedUser.image = value.getString(Constants.KEY_IMAGE);
                            chatAdapter.setReceivedProfileImage(getBitmapFromString(receivedUser.image));
                            chatAdapter.notifyItemRangeChanged(0, chatMessages.size());
                        }
                    }
                    if (isReceiverAvailable) {
                        binding.textName.setCompoundDrawablesWithIntrinsicBounds(
                                R.drawable.ic_baseline_brightness_1_24,
                                0 , 0 , 0);
                    } else {
                        binding.textName.setCompoundDrawablesWithIntrinsicBounds(
                                0, 0 , 0 , 0);
                    }
                }));
    }

    private void listenerMessages() {
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receivedUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receivedUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            return;
        }
        if (value != null) {
            int count = chatMessages.size();
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                    chatMessage.whoSend = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    Date date = new Date();
                    chatMessage.isServerTime = false;
                    if (documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP) != null) {
                        date = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                        chatMessage.isServerTime = true;
                    }
                    chatMessage.dateTime = getReadableTime(date);
                    chatMessage.dateObject = date;
                    chatMessages.add(chatMessage);
                } else if (documentChange.getType() == DocumentChange.Type.MODIFIED) {
                    for (int i = 0; i < chatMessages.size(); i++) {
                        if (!chatMessages.get(i).isServerTime
                                && documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP) != null) {
                            chatMessages.get(i).dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                            chatMessages.get(i).dateTime = getReadableTime(chatMessages.get(i).dateObject);
                            chatMessages.get(i).isServerTime = true;
                            chatAdapter.notifyDataSetChanged();
                            break;
                        }
                    }
                }
            }
            Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
            if (count == 0) {
                chatAdapter.notifyDataSetChanged();
            } else {
                chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);
        if (conversationId == null) {
            checkConversation();
        }
    };

    private void sendMessage() {
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receivedUser.id);
        message.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
        message.put(Constants.KEY_TIMESTAMP, FieldValue.serverTimestamp());
        message.put(Constants.KEY_WHO_SEND, preferenceManager.getString(Constants.KEY_USER_ID));
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        if (conversationId != null) {
            updateConversation(binding.inputMessage.getText().toString(), 0);
        } else {
            HashMap<String, Object> conversation = new HashMap<>();
            conversation.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversation.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversation.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversation.put(Constants.KEY_RECEIVER_ID, receivedUser.id);
            conversation.put(Constants.KEY_RECEIVER_NAME, receivedUser.name);
            conversation.put(Constants.KEY_RECEIVER_IMAGE, receivedUser.image);
            conversation.put(Constants.KEY_LAST_MESSAGE, binding.inputMessage.getText().toString());
            conversation.put((Constants.KEY_TIMESTAMP), FieldValue.serverTimestamp());
            conversation.put(Constants.KEY_SEEN_STATUS, 0);
            conversation.put(Constants.KEY_WHO_SEND, preferenceManager.getString(Constants.KEY_USER_ID));
            conversation.put(Constants.KEY_CONVERSATION_ID, conversationId);

            addConversation(conversation);
        }
        if (!isReceiverAvailable) {
            try {
                JSONArray tokens = new JSONArray();
                tokens.put(receivedUser.token);

                JSONObject data = new JSONObject();
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                data.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());

                JSONObject body = new JSONObject();
                body.put(Constants.REMOTE_MSG_DATA, data);
                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

                sendNotification(body.toString());
            } catch (Exception e) {
                //showToast(e.getMessage());
            }
        }
        binding.inputMessage.setText(null);
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void sendNotification(String messageBody) {
        ApiClient.getClient().create(ApiService.class).sendMessage(
                Constants.getRemoteMsgHeaders(),
                messageBody
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@Nonnull Call<String> call, @Nonnull Response<String> response) {
                if (response.isSuccessful()) {
                    try {
                        if (response.body() != null) {
                            JSONObject responseJson = new JSONObject(response.body());
                            JSONArray results = responseJson.getJSONArray("results");
                            if (responseJson.getInt("failure") == 1) {
                                JSONObject error = (JSONObject) results.get(0);
                                //showToast(error.getString("error"));
                                return;
                            }
                        }
                    } catch (JSONException e) {
                        //e.printStackTrace();
                    }
                }
                else {
                    //showToast("Error: " + response.code());
                }
            }

            @Override
            public void onFailure(@Nonnull Call<String> call, @Nonnull Throwable t) {
                //showToast(t.getMessage());
            }
        });
    }

    private void init() {
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromString(receivedUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        binding.chatRecyclerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private Bitmap getBitmapFromString(String encodedImage) {
        if (encodedImage != null) {
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } else {
            return null;
        }
    }

    private void loadReceivedUserDetails() {
        receivedUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receivedUser.name);
    }

    private void setListener() {
        binding.imageBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
        binding.buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!binding.inputMessage.getText().toString().trim().isEmpty()) {
                    sendMessage();
                }
            }
        });
        binding.imageInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), UserInfoActivity.class);
                intent.putExtra(Constants.KEY_USER_ID, receivedUser.id);
                startActivity(intent);
            }
        });
        binding.inputMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!chatMessages.get(chatMessages.size() - 1).whoSend.equals(preferenceManager.getString(Constants.KEY_USER_ID))) {
                    FirebaseFirestore database = FirebaseFirestore.getInstance();
                    DocumentReference documentReference =
                            database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversationId);
                    documentReference.update(
                            Constants.KEY_SEEN_STATUS, 1
                    );
                }
            }
        });
    }

    private String getReadableTime(Date date) {
        if (date != null) {
            return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
        }
        else {
            return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(new Date());
        }
    }

    private void checkConversation() {
        if (chatMessages.size() != 0) {
            checkForConversationRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receivedUser.id
            );
            checkForConversationRemotely(
                    receivedUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }

    private void checkForConversationRemotely(String senderId, String receiverId) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversationCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversationCompleteListener = task -> {
        if(task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversationId = documentSnapshot.getId();
        }
    };

    private void addConversation(HashMap<String, Object> conversation) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversation)
                .addOnSuccessListener(documentReference -> {
                    conversationId = documentReference.getId();
                    documentReference =
                            database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversationId);
                    documentReference.update(
                            Constants.KEY_CONVERSATION_ID, conversationId
                    );
                });
    }

    private void updateConversation(String message, int seenStatus) {
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversationId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, FieldValue.serverTimestamp(),
                Constants.KEY_SEEN_STATUS, seenStatus,
                Constants.KEY_WHO_SEND, preferenceManager.getString(Constants.KEY_USER_ID),
                Constants.KEY_CONVERSATION_ID, conversationId
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }
}