package com.opendatacam;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends BridgeActivity {

  static {
    System.loadLibrary("native-lib");
    System.loadLibrary("node");
  }

  //We just want one instance of node running in the background.
  public static boolean _startedNodeAlready=false;

  private CameraActivity fragment;
  private int containerViewId = 20;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Initializes the Bridge
    this.init(savedInstanceState, new ArrayList<Class<? extends Plugin>>() {{
      // Additional plugins you've installed go here
      add(CameraObjectDetection.class);



      //fragment = new CameraActivity();
      //fragment.startCamera();

    }});

    // Create container view
    fragment = new CameraActivity();
    FrameLayout containerView = getBridge().getActivity().findViewById(containerViewId);
    if(containerView == null) {
      containerView = new FrameLayout(getApplicationContext());
      containerView.setId(containerViewId);
      containerView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

      getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
      ((ViewGroup)getBridge().getWebView().getParent()).addView(containerView);
      // to back
      getBridge().getWebView().getParent().bringChildToFront(getBridge().getWebView());

      FragmentManager fragmentManager = getSupportFragmentManager();
      FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
      fragmentTransaction.add(containerView.getId(), fragment);
      fragmentTransaction.commit();
    }

    System.out.println("START NODE");
    if( !_startedNodeAlready ) {
      System.out.println("START NODE BECAUSE NOT STARTED YET");
      _startedNodeAlready=true;
      new Thread(new Runnable() {
        @Override
        public void run() {
          //The path where we expect the node project to be at runtime.
          System.out.println("START NODE RUN LOOP");
          String nodeDir=getApplicationContext().getFilesDir().getAbsolutePath()+"/nodejs-project";
          if (wasAPKUpdated()) {
            System.out.println("START NODE APK UPDATED");
            //Recursively delete any existing nodejs-project.
            File nodeDirReference=new File(nodeDir);
            if (nodeDirReference.exists()) {
              deleteFolderRecursively(new File(nodeDir));
            }

            System.out.println("START NODE COPY ASSET FOLDER");
            //Copy the node project from assets into the application's data path.
            copyAssetFolder(getApplicationContext().getAssets(), "nodejs-project", nodeDir);

            System.out.println("START NODE COPY ASSET FOLDER FINISH");
            saveLastUpdateTime();
          }

          System.out.println("START NODE , REALLY");

          try {
            Os.setenv("PORT", "8080", true);
            Os.setenv("NODE_ENV", "production", true);
          } catch (ErrnoException e) {
            e.printStackTrace();
          }

          startNodeWithArguments(new String[]{"node",
                  nodeDir+"/server.js"
          });
        }
      }).start();
    }
  }

  /**
   * A native method that is implemented by the 'native-lib' native library,
   * which is packaged with this application.
   */
  public native void startNodeWithArguments(String[] arguments);

  private static boolean deleteFolderRecursively(File file) {
    try {
      boolean res=true;
      for (File childFile : file.listFiles()) {
        if (childFile.isDirectory()) {
          res &= deleteFolderRecursively(childFile);
        } else {
          res &= childFile.delete();
        }
      }
      res &= file.delete();
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
    try {
      String[] files = assetManager.list(fromAssetPath);
      boolean res = true;

      if (files.length==0) {
        //If it's a file, it won't have any assets "inside" it.
        res &= copyAsset(assetManager,
                fromAssetPath,
                toPath);
      } else {
        new File(toPath).mkdirs();
        for (String file : files)
          res &= copyAssetFolder(assetManager,
                  fromAssetPath + "/" + file,
                  toPath + "/" + file);
      }
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
    InputStream in = null;
    OutputStream out = null;
    try {
      in = assetManager.open(fromAssetPath);
      new File(toPath).createNewFile();
      out = new FileOutputStream(toPath);
      copyFile(in, out);
      in.close();
      in = null;
      out.flush();
      out.close();
      out = null;
      return true;
    } catch(Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private static void copyFile(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[1024];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
  }

  private boolean wasAPKUpdated() {
    SharedPreferences prefs = getApplicationContext().getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
    long previousLastUpdateTime = prefs.getLong("NODEJS_MOBILE_APK_LastUpdateTime", 0);
    long lastUpdateTime = 1;
    try {
      PackageInfo packageInfo = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
      lastUpdateTime = packageInfo.lastUpdateTime;
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }
    return (lastUpdateTime != previousLastUpdateTime);
  }

  private void saveLastUpdateTime() {
    long lastUpdateTime = 1;
    try {
      PackageInfo packageInfo = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
      lastUpdateTime = packageInfo.lastUpdateTime;
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }
    SharedPreferences prefs = getApplicationContext().getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = prefs.edit();
    editor.putLong("NODEJS_MOBILE_APK_LastUpdateTime", lastUpdateTime);
    editor.commit();
  }
}
