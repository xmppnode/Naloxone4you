package org.owntracks.android.data.repos;

import android.databinding.ObservableList;
import org.owntracks.android.messages.MessageCard;
import org.owntracks.android.messages.MessageLocation;
import org.owntracks.android.model.FusedContact;


public interface ContactsRepo {
    ObservableList<FusedContact> getAll();
    FusedContact getById(String id);

    void clearAll();
    void remove(String id);

    void update(String id, MessageLocation m);
    void update(String id, MessageCard m);

    long getRevision();
}