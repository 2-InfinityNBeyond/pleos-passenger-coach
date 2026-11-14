package app.passengercoach

import android.util.Log

/** 차량 값과 위치가 들어가는 로그는 개발용 빌드에서만 남긴다(동의 화면의 "저장하거나 보내지 않아요"와 맞추기 위함). */
inline fun dlog(tag: String, msg: () -> String) {
    if (BuildConfig.DEBUG) Log.i(tag, msg())
}
