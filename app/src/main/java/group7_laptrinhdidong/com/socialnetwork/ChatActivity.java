package group7_laptrinhdidong.com.socialnetwork;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import group7_laptrinhdidong.com.socialnetwork.adapters.AdapterChat;
import group7_laptrinhdidong.com.socialnetwork.models.ModelChat;
import group7_laptrinhdidong.com.socialnetwork.models.ModelUser;

import retrofit2.Call;
import retrofit2.Callback;

public class ChatActivity extends AppCompatActivity {

    //views
    Toolbar toolbar;
    RecyclerView recyclerView;
    ImageView profileIv;
    TextView nameTxv, userStatusTxv;
    EditText messageEdt;
    ImageButton sendBtn;

    //firebase auth
    FirebaseAuth firebaseAuth;
    FirebaseDatabase firebaseDatabase;
    DatabaseReference usersDbRef;

    //for checking if use has seen message or not
    ValueEventListener seenListener;
    DatabaseReference userRefForSeen;

    List<ModelChat> chatList;
    AdapterChat adapterChat;

    String hisUid;
    String myUid;
    String hisImage;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("");
        recyclerView = findViewById(R.id.chat_recyclerView);
        profileIv = findViewById(R.id.profileIv);
        nameTxv = findViewById(R.id.nameTxv);
        userStatusTxv = findViewById(R.id.usersStatusTxv);
        messageEdt = findViewById(R.id.messageEdt);
        sendBtn = findViewById(R.id.sendBtn);

        //layout for RecyclerView
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setStackFromEnd(true);
        //recyclerview properties
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(linearLayoutManager);


        Intent intent = getIntent();
        hisUid = intent.getStringExtra("hisUid");

        //init firebase
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();
        usersDbRef = firebaseDatabase.getReference("Users");

        //search user to get that user's info
        Query userQuery = usersDbRef.orderByChild("uid").equalTo(hisUid);
        //get user picture and name
        userQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // check until required info is received

                for(DataSnapshot ds : dataSnapshot.getChildren()) {
                    //get data
                    String name = "" + ds.child("name").getValue();
                    hisImage = "" + ds.child("image").getValue();

                    //get value of onlinestatus
                    String onlineStatus = ""+ ds.child("onlineStatus").getValue();

                    if(onlineStatus.equals("online")){
                        userStatusTxv.setText(onlineStatus);
                    } else{
                        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
                        cal.setTimeInMillis(Long.parseLong(onlineStatus));
                        String dateTime = DateFormat.format("dd/MM/yyyy hh:mm aa", cal).toString();
                        userStatusTxv.setText("Last seen at: "+dateTime);
                    }

                    //set data
                    nameTxv.setText(name);
                    try {
                        Picasso.get().load(hisImage).placeholder(R.drawable.ic_default).into(profileIv);

                    } catch (Exception e) {
                        Picasso.get().load(R.drawable.ic_default).into(profileIv);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        //handle button send
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //get text from edit text
                String message = messageEdt.getText().toString().trim();
                //check if text empty or not
                if(TextUtils.isEmpty(message)){
                    Toast.makeText(ChatActivity.this, "Can't send the empty message...", Toast.LENGTH_SHORT).show();

                } else {
                    sendMessage(message);
                }

                //reset edit text
                messageEdt.setText("");
            }
        });

        readMessage();

        seenMessage();
    }

    private void seenMessage() {
        userRefForSeen = FirebaseDatabase.getInstance().getReference("Chats");
        seenListener = userRefForSeen.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for(DataSnapshot ds:dataSnapshot.getChildren()){
                    ModelChat chat = ds.getValue(ModelChat.class);
                    if(chat.getReceiver().equals(myUid) && chat.getSender().equals(hisUid)){
                        HashMap<String, Object> hasSeenHashMap = new HashMap<>();
                        hasSeenHashMap.put("isSeen", true);
                        ds.getRef().updateChildren(hasSeenHashMap);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void readMessage() {
        chatList = new ArrayList<>();
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("Chats");
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                chatList.clear();
                for(DataSnapshot ds: dataSnapshot.getChildren()){
                    ModelChat chat = ds.getValue(ModelChat.class);
                    if(chat.getReceiver().equals(myUid)&&chat.getSender().equals(hisUid) ||
                            chat.getReceiver().equals(hisUid)&&chat.getSender().equals(myUid)){
                        chatList.add(chat);
                    }

                    adapterChat = new AdapterChat(ChatActivity.this,chatList,hisImage);
                    adapterChat.notifyDataSetChanged();

                    recyclerView.setAdapter(adapterChat);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void sendMessage(final String message) {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference();

        String timestamp = String.valueOf(System.currentTimeMillis());

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("sender", myUid);
        hashMap.put("receiver", hisUid);
        hashMap.put("message", message);
        hashMap.put("timestamp", timestamp);
        hashMap.put("isSeen", false);
        databaseReference.child("Chats").push().setValue(hashMap);

    }


    private void checkUserStatus(){
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if(user!=null){
            myUid = user.getUid();
        }
        else{
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    private void checkOnlineStatus(String status){
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("Users").child(myUid);
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("onlineStatus", status);
        dbRef.updateChildren(hashMap);
    }

    @Override
    protected void onStart() {
        checkUserStatus();
        checkOnlineStatus("online");
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //check offline with last seen
        String timestamp = String.valueOf(System.currentTimeMillis());
        checkOnlineStatus(timestamp);
        userRefForSeen.removeEventListener(seenListener);
    }

    @Override
    protected void onResume() {
        checkOnlineStatus("online");
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        //hide search view
        menu.findItem(R.id.action_search).setVisible(false);
        menu.findItem(R.id.action_add_post).setVisible(false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        if(id==R.id.action_logout){
            firebaseAuth.signOut();
            checkUserStatus();
        }

        return super.onOptionsItemSelected(item);
    }
}
