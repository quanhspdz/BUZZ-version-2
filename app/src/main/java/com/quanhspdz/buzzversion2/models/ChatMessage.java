package com.quanhspdz.buzzversion2.models;

import com.google.firebase.Timestamp;

import java.util.Date;

public class ChatMessage {
    public String senderId, receiverId, message, dateTime;
    public Date dateObject;
    public Timestamp timestamp;
    public String conversationId, conversationName, conversationImage, whoSend, conversionId;
    public Boolean isServerTime;
    public Long seenStatus;
}
