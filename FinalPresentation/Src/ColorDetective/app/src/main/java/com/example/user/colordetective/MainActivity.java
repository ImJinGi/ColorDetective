package com.example.user.colordetective;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Color;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener, CameraBridgeViewBase.CvCameraViewListener2
{

    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;

    private Scalar mBlobColorHsv;
    private Scalar mBlobColorRgba;

    TextView touch_coordinates;
    TextView touch_color;
    ImageView color_rect;

    double x = -1;
    double y = -1;

    private final int stateRGB = 1;
    private final int stateHSV = 2;
    private int nowState = 1;

    static int REQUEST_PICTURE=1;
    static int REQUEST_PHOTO_ALBUM=2;

    Dialog dialog;
    Context context;

    private BaseLoaderCallback mLoaderCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 전체화면 설정
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!hasPermissions(PERMISSIONS)) {
                requestPermissions(PERMISSIONS, PERMISSION_REQUEST_CODE);
            }
        }

        // activity_main.xml의 text 설정
        touch_coordinates = (TextView) findViewById(R.id.touch_coordinates);
        touch_color = (TextView) findViewById(R.id.touch_color);
        color_rect = (ImageView) findViewById(R.id.imageView);

        // openCV 카메라 뷰 설정
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        context = this;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallBack);
        } else {
            mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        //카메라 출력
        mRgba = inputFrame.rgba();
        return mRgba;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        double yLow = (double) mOpenCvCameraView.getHeight() * 0;
        double yHigh = (double) mOpenCvCameraView.getHeight();

        double xScale = (double) cols / (double) mOpenCvCameraView.getWidth();
        double yScale = (double) rows / (yHigh - yLow);

        x = event.getX();
        y = event.getY();

        y = y - yLow;

        x = x * xScale;
        y = y * yScale;

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        touch_coordinates.setText("X: " + String.format("%.2f", x) + ", Y: " + String.format("%.2f", y));
        Rect touchedRect = new Rect();

        touchedRect.x = (int) x;
        touchedRect.y = (int) y;

        touchedRect.width = 8;
        touchedRect.height = 8;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width * touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++) {
            mBlobColorHsv.val[i] /= pointCount;
        }
        mBlobColorRgba = convertScalarHsv2Rgba(mBlobColorHsv);

        // RGB 버튼 터치 시 RGB정보 출력
        if (nowState == stateRGB) {
            touch_color.setText("Color R: " + (int)mBlobColorRgba.val[0]
                    + ", G: " + (int)mBlobColorRgba.val[1]
                    + ", B: " + (int)mBlobColorRgba.val[2]);

        }
        // HSV버튼 터치 시 HSV정보 출력
        else if (nowState == stateHSV) {
            touch_color.setText("Color H: " + (int)mBlobColorHsv.val[0]
                    + ", S: " + (int)mBlobColorHsv.val[1]
                    + ", V: " + (int)mBlobColorHsv.val[2]);
        }

        // 터치한 부분의 색상 정보를 바탕으로 사각형의 색상 변경
        color_rect.setBackgroundColor(Color.rgb((int) mBlobColorRgba.val[0],
                (int) mBlobColorRgba.val[1],
                (int) mBlobColorRgba.val[2]));

        return false;
    }

    private Scalar convertScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    //////////////////////////카메라 권한 관련//////////////////////////////
    static final int PERMISSION_REQUEST_CODE = 1000;
    String[] PERMISSIONS = {"android.permission.CAMERA"};

    private boolean hasPermissions(String[] permissions) {
        int result;

        for (String perms : permissions) {
            result = ContextCompat.checkSelfPermission(this, perms);
            if (result == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean cameraPermissionAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    if (!cameraPermissionAccepted) {
                        showDialogForPermission("");
                    }
                }
                break;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("bulderTitle");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("bulderTitle", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                requestPermissions(PERMISSIONS, PERMISSION_REQUEST_CODE);
            }
        });

        builder.setNegativeButton("bulderTitleNegative", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.create().show();
    }

    /////////////////////////////////button///////////////////////////
    public void onClick(View view) {
        switch (view.getId()) {
            // Color Dictionary
            case R.id.colorTableButton:
                Toast.makeText(this, "ColorTable show", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(this, ColorTable.class);
                startActivity(intent);
                break;
                // RGB -> HSV 변환 버튼
            case R.id.RgbToHsvButton:
                Toast.makeText(this, "Change RGB to HSV", Toast.LENGTH_LONG).show();
                nowState = 2;
                break;
            // HSV -> RGB 변환 버튼
            case R.id.HsvToRgbButton:
                Toast.makeText(this, "Change HSV to RGB", Toast.LENGTH_LONG).show();
                nowState = 1;
                break;
                // 사진 촬영 intent 전환
            case R.id.takePicture:
                Intent intent2 = new Intent(this, TakePicture.class);
                startActivity(intent2);
                break;
                // 이미지 불러오기 intent 전환
            case R.id.photoAlbum:
                Intent intent3 = new Intent(this, SelectPicture.class);
                startActivity(intent3);
                break;
                // 사용 설명 AlertDialog로 보여주기
            case R.id.howtouse:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("How to Use this Application");
                builder.setMessage("- This application prints the color information of the point of you touched. "
                 + "The small rectangle next to the color information shows the color that you touched point.\n"
                 + "- You can change the Color Model Rgb to Hsv and Hsv to Rgb.\n"
                 + "- Also, you can take and load a picture."
                 + "If you load the picture and touch one point of the picture, you can get the color information of that point.\n"
                 + "- This app provide 'Color Dictionary'.\n"
                 + "- I hope this app will be helpful.\n"
                 + "- Thank you!");
                builder.setPositiveButton("YES",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
                builder.show();
                break;
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PHOTO_ALBUM && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Intent intent = new Intent(this, SelectPicture.class);
            startActivity(intent);
        }
        else if (requestCode == REQUEST_PICTURE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Intent intent2 = new Intent(this, TakePicture.class);
            startActivity(intent2);
        }
    }
}
