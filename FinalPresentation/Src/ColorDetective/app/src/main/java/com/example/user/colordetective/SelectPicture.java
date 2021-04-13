package com.example.user.colordetective;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraGLSurfaceView;
import org.opencv.android.CameraRenderer;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static com.example.user.colordetective.BuildConfig.DEBUG;

public class SelectPicture extends AppCompatActivity{
    private static final String TAG = "SelectPicture";

    static int REQUEST_PHOTO_ALBUM=2;

    private Mat mRgba;

    private Scalar mBlobColorHsv;
    private Scalar mBlobColorRgba;

    TextView touch_coordinates;
    TextView colorRgb;
    TextView colorHsv;

    double x = -1;
    double y = -1;

    ImageView imageView;
    Bitmap bitmap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_picture);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent3 = new Intent();
        intent3.setType("image/*");
        intent3.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent3, "Select Picture"), REQUEST_PHOTO_ALBUM);

        touch_coordinates = (TextView) findViewById(R.id.touch_coordinates);
        colorRgb = (TextView) findViewById(R.id.colorRgb);
        colorHsv = (TextView) findViewById(R.id.colorHsv);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PHOTO_ALBUM && resultCode == RESULT_OK && data != null && data.getData() != null) {

            Uri uri = data.getData();

            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);

                mRgba = new Mat();
                Utils.bitmapToMat(bitmap, mRgba);
                imageView = (ImageView) findViewById(R.id.imageView3);
                imageView.setImageBitmap(bitmap);
                imageView.setClickable(true);
                imageView.callOnClick();

                /*
                 * 터치한 이미지의 색상 정보 처리 및 출력
                 */
                imageView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        int cols = mRgba.cols();
                        int rows = mRgba.rows();

                        double yLow = (double) imageView.getHeight() * 0;
                        double yHigh = (double) imageView.getHeight();

                        double xScale = (double) cols / (double) imageView.getWidth();
                        double yScale = (double) rows / (yHigh - yLow);

                        x = event.getX();
                        y = event.getY();

                        y = y - yLow;

                        x = x * xScale;
                        y = y * yScale;

                        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

                        touch_coordinates.setText("X: " + String.format("%.2f", x) + ", Y: " + String.format("%.2f", y));
                        Rect touchedRect = new Rect();

                        touchedRect.x = (int) x;;
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

                        colorRgb.setText("(R, G, B): (" + (int)mBlobColorRgba.val[0]
                                + ", " + (int)mBlobColorRgba.val[1]
                                + ", " + (int)mBlobColorRgba.val[2]
                                + ")");
                        colorHsv.setText("(H, S, V): (" + (int)mBlobColorHsv.val[0]
                                + ", " + (int)mBlobColorHsv.val[1]
                                + ", " + (int)mBlobColorHsv.val[2]
                                + ")");
                        colorRgb.setTextColor(Color.rgb((int) mBlobColorRgba.val[0],
                                (int) mBlobColorRgba.val[1],
                                (int) mBlobColorRgba.val[2]));
                        colorHsv.setTextColor(Color.rgb((int) mBlobColorRgba.val[0],
                                (int) mBlobColorRgba.val[1],
                                (int) mBlobColorRgba.val[2]));
                        return false;
                    }
                });
                {

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private Scalar convertScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }
}
