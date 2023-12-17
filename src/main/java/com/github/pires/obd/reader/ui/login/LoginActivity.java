package com.github.pires.obd.reader.ui.login;

import com.github.pires.obd.reader.R;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.pires.obd.reader.activity.MainActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private static final String TAG = "LoginActivity";
    private String authToken;
    private static final int RC_SIGN_IN = 123; // Pode ser qualquer número inteiro único

    private String userEmail;
    private String userId;
    private String userToken;


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // Obtém a conta Google autenticada
                GoogleSignInAccount account = task.getResult(ApiException.class);

                // Autentica no Firebase com a credencial do Google
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                mAuth.signInWithCredential(credential)
                        .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    // Sign in success, update UI with the signed-in user's information
                                    Log.d(TAG, "signInWithCredential:success");
                                    FirebaseUser user = mAuth.getCurrentUser();
                                    if (user != null) {
                                        authToken = user.getIdToken(false).getResult().getToken();
                                        userEmail = user.getEmail();
                                        startMainActivity();
                                    }
                                } else {
                                    // If sign in fails, display a message to the user.
                                    Log.e(TAG, "signInWithCredential:failure", task.getException());
                                    // Toast.makeText(LoginActivity.this, "Authentication failed.",
                                    //        Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            } catch (ApiException e) {
                Log.e(TAG, "Google sign-in failed", e);
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
//                                userId = user.getUid();
                                userEmail = user.getEmail();
                                userId = user.getUid();
//                                userToken = user.getIdToken();

                                authToken = user.getIdToken(false).getResult().getToken();
                                startMainActivity();
                            }
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.e(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

//        getSupportActionBar("OBD-II Reader");

        mAuth = FirebaseAuth.getInstance();

        Button loginButton = findViewById(R.id.google_button);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signIn();
            }
        });
    }

    private void signIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.web_client_id)) // Adicione esta linha
                .requestEmail()
                .build();

        GoogleSignInClient signInClient = GoogleSignIn.getClient(this, gso);

        Intent signInIntent = signInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);

//        mAuth.signInWithCredential(credential)
//                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
//                    @Override
//                    public void onComplete(@NonNull Task<AuthResult> task) {
//                        if (task.isSuccessful()) {
//                            // Sign in success, update UI with the signed-in user's information
//                            Log.d(TAG, "signInWithCredential:success");
//                            FirebaseUser user = mAuth.getCurrentUser();
//                            if (user != null) {
//                                authToken = user.getIdToken(false).getResult().getToken();
//                                startMainActivity();
//                            }
//                        } else {
//                            // If sign in fails, display a message to the user.
//                            Log.e(TAG, "signInWithCredential:failure", task.getException());
//                            // Toast.makeText(LoginActivity.this, "Authentication failed.",
//                            //        Toast.LENGTH_SHORT).show();
//                        }
//                    }
//                });


//        mAuth.signInAnonymously()
//                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
//                    @Override
//                    public void onComplete(@NonNull Task<AuthResult> task) {
//                        if (task.isSuccessful()) {
//                            // Sign in success, update UI with the signed-in user's information
//                            Log.d(TAG, "signInAnonymously:success");
//                            FirebaseUser user = mAuth.getCurrentUser();
//                            if (user != null) {
//                                authToken = user.getIdToken(false).getResult().getToken();
//                                startMainActivity();
//                            }
//                        } else {
//                            // If sign in fails, display a message to the user.
//                            Log.e(TAG, "signInAnonymously:failure", task.getException());
////                            Toast.makeText(LoginActivity.this, "Authentication failed.",
////                                    Toast.LENGTH_SHORT).show();
//                        }
//                    }
//                });
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("authToken", authToken);
        intent.putExtra("userEmail", userEmail);
        startActivity(intent);
        finish();
    }
}
