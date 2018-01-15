package pr.hyperlpr;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sfshine on 17-11-11.
 */

public class PermissionUtil {
    OnPermissionListener onPermisstionListener;
    int mRequestCode;
    Activity mActivity;

    public interface OnPermissionListener {
        public void onSuccess();

        public void onFail(List<String> deniedPermissions);
    }


    public void checkPermission(final Activity activity, int requestCode, String[] permissions, OnPermissionListener onPermissionListener) {
        mActivity = activity;
        mRequestCode = requestCode;
        onPermisstionListener = onPermissionListener;
        List<String> permissionNoGranted = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 23) {
            for (String s : permissions) {
                if (ContextCompat.checkSelfPermission(activity, s) != PackageManager.PERMISSION_GRANTED) {
                    permissionNoGranted.add(s);
                }
            }
            if (permissionNoGranted.size() > 0) {
                ActivityCompat.requestPermissions(activity, permissionNoGranted.toArray(new String[permissionNoGranted.size()]), mRequestCode);
            } else {
                if (null != onPermisstionListener)
                    onPermisstionListener.onSuccess();
            }
        } else {// <= 23不进行权限检查
            if (null != onPermisstionListener)
                onPermisstionListener.onSuccess();
        }

    }

    /**
     * 代理Activity的权限管理回调
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     * @return
     */
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean needIntercept = (requestCode == this.mRequestCode);
        if (needIntercept) {
            List<String> permissionGrantedFailed = new ArrayList<>();
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    permissionGrantedFailed.add(permissions[i]);
                }
            }
            if (permissionGrantedFailed.size() > 0) {
                if (null != onPermisstionListener)
                    onPermisstionListener.onFail(permissionGrantedFailed);
            } else {
                if (null != onPermisstionListener)
                    onPermisstionListener.onSuccess();
            }
        }
        return needIntercept;
    }
}
