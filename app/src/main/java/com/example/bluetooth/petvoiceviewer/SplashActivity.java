package com.example.bluetooth.petvoiceviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import com.anggrayudi.storage.SimpleStorageHelper;
import com.anggrayudi.storage.permission.ActivityPermissionRequest;
import com.anggrayudi.storage.permission.PermissionCallback;
import com.anggrayudi.storage.permission.PermissionReport;
import com.anggrayudi.storage.permission.PermissionResult;

import org.jetbrains.annotations.NotNull;

import java.util.List;


public class SplashActivity extends AppCompatActivity {
    Handler startHandler = new Handler();
    private final String TAG = "Splash";
    private final ActivityPermissionRequest permissionRequest = new ActivityPermissionRequest.Builder(this)
            .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            .withCallback(new PermissionCallback() {
                @Override
                public void onPermissionsChecked(@NotNull PermissionResult result, boolean fromSystemDialog) {
                    String grantStatus = result.getAreAllPermissionsGranted() ? getString(R.string.permission_granted) : getString(R.string.permission_denied);
                    Toast.makeText(getBaseContext(), grantStatus, Toast.LENGTH_SHORT).show();
                    if (result.getAreAllPermissionsGranted()) {
                        startMain();
                    } else {
                        permissionRequest.check();
                    }
                }

                @Override
                public void onShouldRedirectToSystemSettings(@NotNull List<PermissionReport> blockedPermissions) {
                    SimpleStorageHelper.redirectToSystemSettings(SplashActivity.this);
                }
            })
            .build();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
    }

    @Override
    protected void onStart() {
        super.onStart();
        startHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkpermission();
            }
        }, 2000);
    }

    public void checkpermission() {
        permissionRequest.check();
    }


    public void startMain() {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}