package com.phlox.tvwebbrowser

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat.requestPermissions
import com.phlox.tvwebbrowser.activity.IncognitoModeMainActivity
import com.phlox.tvwebbrowser.utils.QbCoreFileUtils
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModelsRepository
import com.tencent.smtt.export.external.TbsCoreSettings
import com.tencent.smtt.sdk.QbSdk
import com.tencent.smtt.sdk.TbsListener
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


/**
 * Created by PDT on 09.09.2016.
 */
class TVBro : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        lateinit var instance: TVBro
        const val CHANNEL_ID_DOWNLOADS: String = "downloads"
        const val MAIN_PREFS_NAME = "main.xml"
        val TAG = TVBro::class.simpleName

        val config: Config get() = instance._config
    }

    lateinit var threadPool: ThreadPoolExecutor
        private set

    private lateinit var _config: Config
    private var isInitTbs = false
    private fun initX5WebView() {
        //允许无WIFI下载
        QbSdk.setDownloadWithoutWifi(true)
        //切换为本地安装模式，当前方案仅支持arm64
        isInitTbs = QbSdk.canLoadX5(getApplicationContext());
        if (!isInitTbs || QbSdk.getTbsVersion(getApplicationContext()) < 46007) {
            QbCoreFileUtils.copyAssets(getApplicationContext(), "46141.apk", QbCoreFileUtils.getTBSFileDir(
                applicationContext
            ).path + "/46141.apk");
        }
        // 在调用TBS初始化、创建WebView之前进行如下配置
        val map: HashMap<String, Any> = HashMap()
        map[TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER] = true
        map[TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE] = true
        QbSdk.initTbsSettings(map)

        val canLoadX5 = QbSdk.canLoadX5(applicationContext)
        Log.e(TAG, "canLoadX5: " + canLoadX5+"|TbsVersion:"+QbSdk.getTbsVersion(applicationContext))
        if (canLoadX5) {
            Log.e(TAG,"成功安装了X5内核可喜可贺")
            return;
        }
        QbSdk.reset(applicationContext)
        QbSdk.setTbsListener(object : TbsListener {
            override fun onDownloadFinish(i: Int) {}
            override fun onInstallFinish(i: Int) {
                Log.e(TAG, "onInstallFinish: $i")
                val tbsVersion = QbSdk.getTbsVersion(applicationContext)
                Log.e(TAG, "tbsVersion: $tbsVersion")
            }
            override fun onDownloadProgress(i: Int) {}
        })
        QbSdk.reset(applicationContext);
        QbSdk.installLocalTbsCore(applicationContext, 46141, QbCoreFileUtils.getTBSFileDir(getApplicationContext()).getPath() + "/46141.apk");
    }
    override fun onCreate() {

        super.onCreate()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            //we need this since when targetSdkVersion >= 33 then
            //deprecated WebSettingsCompat.setForceDark stops working on android 9 and below
            applicationInfo.targetSdkVersion = 32
        }
        instance = this

        _config = Config(getSharedPreferences(MAIN_PREFS_NAME, 0))

        val maxThreadsInOfflineJobsPool = Runtime.getRuntime().availableProcessors()
        threadPool = ThreadPoolExecutor(0, maxThreadsInOfflineJobsPool, 20,
                TimeUnit.SECONDS, ArrayBlockingQueue(maxThreadsInOfflineJobsPool))
        initWebView()
        initX5WebView()
        initNotificationChannels()

        ActiveModelsRepository.init(this)

        when (_config.theme) {
            Config.Theme.BLACK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            Config.Theme.WHITE -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        registerActivityLifecycleCallbacks(this)
    }

    private fun initWebView() {
        Log.i(TAG, "initWebView")
        //CookieHandler I don't know what it can do...
/*        val cookieManager = CookieManager()
        CookieHandler.setDefault(cookieManager)*/
    }

    private fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.downloads)
            val descriptionText = getString(R.string.downloads_notifications_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID_DOWNLOADS, name, importance)
            channel.description = descriptionText
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        val permissions = arrayOf<String>(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        //验证是否许可权限
        for (str in permissions) {
            if (checkSelfPermission(str) != PackageManager.PERMISSION_GRANTED) {
                //申请权限
                requestPermissions(activity,permissions,1)
                Toast.makeText(activity,"请同意权限后手动关闭软件……申请权限并同意后，请重启软件两次再去查看内核情况",Toast.LENGTH_SHORT).show()
                return
            }
        }
    }
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        //we need this because in case of IncognitoModeMainActivity closed by exit button by user
        //then incognito mode becomes closed but process are still running and this lead to
        //strange problems at next time when we trying to start the incognito mode
        if (activity is IncognitoModeMainActivity) {
            Process.killProcess(Process.myPid())
        }
    }
}
