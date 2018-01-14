package pr.hyperlpr;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.photo.BitmapPhoto;
import io.fotoapparat.result.PendingResult;
import io.fotoapparat.result.PhotoResult;
import io.fotoapparat.view.CameraView;

/**
 * Created by sfshine on 17-11-11.
 */

public class CameraActivity extends Activity {
    private static final String TAG = "CameraActivity";
    private CameraView cameraView;
    private Fotoapparat fotoapparat;
    private static final String sdcarddir = "/sdcard/" + DeepCarUtil.ApplicationDir;

    private Handler mHandler = new Handler();
    private long handle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        cameraView = (CameraView) findViewById(R.id.camera_view);
        fotoapparat = Fotoapparat.with(this).into(cameraView).build();
        cameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    System.loadLibrary("hyperlpr");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            initRecognizer();
                        }
                    }).start();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private void takePicture() {
        PhotoResult photoResult = fotoapparat.takePicture();
        photoResult
                .toBitmap()
                .whenAvailable(new PendingResult.Callback<BitmapPhoto>() {
                    @Override
                    public void onResult(BitmapPhoto result) {
                        ImageView imageView = (ImageView) findViewById(R.id.result);
                        Bitmap bitmap = rotate(90, result.bitmap, true);
                        recognize(bitmap);
                        imageView.setImageBitmap(bitmap);
                    }
                });
    }

    public static Bitmap rotate(int angle, Bitmap bitmap, boolean recycleSrc) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (recycleSrc && resizedBitmap != bitmap && bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
            bitmap = null;
        }
        return resizedBitmap;
    }

    private void recognize(final Bitmap bmp) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int width = bmp.getWidth();
                int height = bmp.getHeight();
                Mat m = new Mat(width, height, CvType.CV_8UC4);
                Utils.bitmapToMat(bmp, m);
                if (width > 1000 || height > 1000) {
                    Size sz = new Size(600, 800);
                    Imgproc.resize(m, m, sz);
                }
                try {
                    final String license = DeepCarUtil.SimpleRecognization(m.getNativeObjAddr(), handle);
                    Message msg = new Message();
                    Bundle b = new Bundle();
                    b.putString("license", license);
                    b.putParcelable("bitmap", bmp);
                    msg.what = 1;
                    msg.setData(b);
                    Log.d(TAG, "license " + license);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(CameraActivity.this, license, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    Log.d(TAG, "exception occured!");
                }
            }
        }).start();
    }


    @Override
    protected void onStart() {
        super.onStart();
        fotoapparat.start();

    }

    @Override
    protected void onStop() {
        super.onStop();
        fotoapparat.stop();
    }

    public void initRecognizer() {
        String cascade_filename = sdcarddir + File.separator + DeepCarUtil.cascade_filename;
        String finemapping_prototxt = sdcarddir + File.separator + DeepCarUtil.finemapping_prototxt;
        String finemapping_caffemodel = sdcarddir + File.separator + DeepCarUtil.finemapping_caffemodel;
        String segmentation_prototxt = sdcarddir + File.separator + DeepCarUtil.segmentation_prototxt;
        String segmentation_caffemodel = sdcarddir + File.separator + DeepCarUtil.segmentation_caffemodel;
        String character_prototxt = sdcarddir + File.separator + DeepCarUtil.character_prototxt;
        String character_caffemodel = sdcarddir + File.separator + DeepCarUtil.character_caffemodel;
        DeepAssetUtil.CopyAssets(this, DeepCarUtil.ApplicationDir, sdcarddir);
        handle = DeepCarUtil.InitPlateRecognizer(
                cascade_filename,
                finemapping_prototxt, finemapping_caffemodel,
                segmentation_prototxt, segmentation_caffemodel,
                character_prototxt, character_caffemodel
        );
        Log.i(TAG, "initRecognizer successfully handle = " + handle);
    }
}
