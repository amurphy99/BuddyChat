package com.example.buddychat.utils.behavior;

import android.os.RemoteException;
import android.util.Log;

import com.bfr.buddy.ui.shared.IUIFaceAnimationCallback;
import com.bfr.buddy.ui.shared.FacialEvent;
import com.bfr.buddysdk.BuddySDK;

public final class FacialEvents {
    private static final String TAG = "[DPU_FacialEvents]";
    private FacialEvents() {} // no instances

    private static void playFacialEvent() {

        BuddySDK.UI.playFacialEvent(FacialEvent.FALL_ASLEEP, 0.5, new IUIFaceAnimationCallback.Stub() {
            @Override public void onAnimationEnd(String s, String s1) throws RemoteException {
                Log.i(TAG, String.format("%s Done laying facial event: %s | %s", TAG, s, s1));
            }
        });

    }

}
