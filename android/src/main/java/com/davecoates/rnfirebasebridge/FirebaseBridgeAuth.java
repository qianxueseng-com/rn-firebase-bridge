package com.davecoates.rnfirebasebridge;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;
import com.google.android.gms.tasks.*;
import com.google.firebase.auth.*;

import java.util.Map;
import java.util.HashMap;

public class FirebaseBridgeAuth extends ReactContextBaseJavaModule {

  private static final String TAG = "FirebaseBridgeAuth";

  private FirebaseAuth mAuth;

  public FirebaseBridgeAuth(ReactApplicationContext reactContext) {
    super(reactContext);
    mAuth = FirebaseAuth.getInstance();
    LifecycleEventListener listener = new LifecycleEventListener() {
      @Override
      public void onHostResume() {
      }

      @Override
      public void onHostPause() {
      }
      @Override
      public void onHostDestroy() {
        if (mAuthListener != null) {
          mAuth.removeAuthStateListener(mAuthListener);
        }
      }
    };
    reactContext.addLifecycleEventListener(listener);
  }

  private void sendEvent(String eventName,
                         @Nullable WritableMap params) {
    ReactContext reactContext = getReactApplicationContext();
    reactContext
            .getJSModule(RCTNativeAppEventEmitter.class)
            .emit(eventName, params);
  }

  @Override
  public String getName() {
    return "FirebaseBridgeAuth";
  }

  public WritableMap convertUser(FirebaseUser user) {
    final WritableMap m = Arguments.createMap();
    m.putString("uid", user.getUid());
    m.putString("email", user.getEmail());
    m.putString("displayName", user.getDisplayName());
    if (user.getPhotoUrl() != null) {
      m.putString("photoUrl", user.getPhotoUrl().toString());
    }
    m.putBoolean("anonymous", user.isAnonymous());
    return m;
  }

  private FirebaseAuth.AuthStateListener mAuthListener;

  @ReactMethod
  public void addAuthStateDidChangeListener() {
    if (mAuthListener == null) {

      mAuthListener = new FirebaseAuth.AuthStateListener() {
        @Override
        public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
          FirebaseUser user = firebaseAuth.getCurrentUser();
          if (user != null) {
            sendEvent("authStateDidChange", convertUser(user));
          } else {
            sendEvent("authStateDidChange", null);
          }
        }
      };
      mAuth.addAuthStateListener(mAuthListener);
    }
  }

  @ReactMethod
  public void signInWithEmail(String email, String password, final Promise promise) {
    FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
              @Override
              public void onComplete(@NonNull Task<AuthResult> task) {
                Log.d(TAG, "User re-authenticated.");
                try {
                  AuthResult result = task.getResult();
                  promise.resolve(convertUser(result.getUser()));
                } catch (RuntimeExecutionException e) {
                  promise.reject(e);
                }
              }
            });
  }

  @ReactMethod
  public void createUserWithEmail(String email, String password, final Promise promise) {
    FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
              @Override
              public void onSuccess(AuthResult authResult) {
                promise.resolve(convertUser(authResult.getUser()));
              }
            })
            .addOnFailureListener(new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                // Error codes as per https://firebase.google.com/docs/reference/js/firebase.auth.Auth#createUserWithEmailAndPassword
                if (e instanceof FirebaseAuthWeakPasswordException) {
                  promise.reject("auth/weak-password", e.getMessage());
                } else if (e instanceof FirebaseAuthUserCollisionException) {
                  promise.reject("auth/email-already-in-use", e.getMessage());
                } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                  promise.reject("auth/invalid-email", e.getMessage());
                } else {
                  promise.reject(e);
                }
              }
            });
  }

}
