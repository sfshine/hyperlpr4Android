package pr.hyperlpr;

import android.Manifest;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
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
import java.util.List;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.photo.BitmapPhoto;
import io.fotoapparat.result.PendingResult;
import io.fotoapparat.result.PhotoResult;
import io.fotoapparat.view.CameraView;

/**
 * Created by sfshine on 17-11-11.
 */

public class CameraActivity extends Activity {
    private static final String TAG = "CameraActivity___";
    public static final int REQUEST_CODE = 2333;
    private CameraView cameraView;
    private Fotoapparat fotoapparat;
    private static final String sdcarddir = "/" + Environment.getExternalStorageDirectory() + "/" + DeepCarUtil.ApplicationDir;

    private Handler mHandler = new Handler();
    private long handle;

    /*检查权限*/
    String[] permissions = new String[]{Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};//在SDCard中创建与删除文件权限    /**
    PermissionUtil mPermissionUtil = new PermissionUtil();

    private boolean isCameraStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        cameraView = (CameraView) findViewById(R.id.camera_view);
        fotoapparat = Fotoapparat.with(CameraActivity.this).into(cameraView).build();
        cameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCameraStart) takePicture();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkPermissionAndStartCamera();
    }


    @Override
    protected void onStop() {
        super.onStop();
        stopCameraSafety();
    }

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

    public void checkPermissionAndStartCamera() {
        mPermissionUtil.checkPermission(this, REQUEST_CODE, permissions, new PermissionUtil.OnPermissionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "permisson onSuccess ");
                if (!OpenCVLoader.initDebug()) {
                    Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
                    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, CameraActivity.this, mLoaderCallback);
                } else {
                    Log.d(TAG, "OpenCV library found inside package. Using it!");
                    mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
                }
                startCameraSafety();
            }

            @Override
            public void onFail(List<String> deniedPermissions) {
                Log.d(TAG, "permisson " + deniedPermissions.toString());
            }
        });
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


    private void startCameraSafety() {
        if (fotoapparat != null && !isCameraStart) {
            fotoapparat.start();
            isCameraStart = true;

        }
    }

    private void stopCameraSafety() {
        if (fotoapparat != null && isCameraStart) {
            fotoapparat.stop();
            isCameraStart = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mPermissionUtil.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}
