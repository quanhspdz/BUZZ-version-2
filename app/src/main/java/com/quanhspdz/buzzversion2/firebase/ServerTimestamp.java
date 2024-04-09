package com.quanhspdz.buzzversion2.firebase;

import java.util.Date;

public class ServerTimestamp {
    @com.google.firebase.firestore.ServerTimestamp
    private Date timestamp;

    public Date getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

}
