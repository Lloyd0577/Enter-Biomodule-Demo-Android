package cn.entertech.biomoduledemo

import android.Manifest
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.ViewPager
import android.view.View
import android.widget.Toast
import cn.entertech.ble.FlowtimeBleManager
import com.google.gson.Gson
import com.orhanobut.logger.Logger
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var flowtimeBleManager: FlowtimeBleManager
    private lateinit var socketManager: WebSocketManager
    private lateinit var messageReceiveFragment: MessageReceiveFragment
    private lateinit var messageSendFragment: MessageSendFragment
    lateinit var vpContainer: ViewPager
    lateinit var pagerSlidingTabStrip: PagerSlidingTabStrip
    lateinit var sessionId:String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        flowtimeBleManager = FlowtimeBleManager.getInstance(this)
        socketManager = WebSocketManager.getInstance()
        socketManager.connect()
        initPermission()
        initView()
    }


    class MessageAdapter(fragmentManager: FragmentManager, fragments: List<Fragment>, titles: List<String>) :
        FragmentStatePagerAdapter(fragmentManager) {
        private var fragments: List<Fragment> = listOf()
        private var titles: List<String> = listOf()

        init {
            this.fragments = fragments
            this.titles = titles
        }

        override fun getItem(position: Int): Fragment {
            return fragments.get(position)
        }

        override fun getCount(): Int {
            return fragments.size
        }

        override fun getPageTitle(position: Int): CharSequence {
            return titles.get(position)
        }
    }

    var brainDataCallback = fun(result: String?) {
        if (result == null){
            return
        }
        if (result!!.contains("session_id")){
            var startResponse = Gson().fromJson<StartResponse>(result,StartResponse::class.java)
            Logger.d("start response is $startResponse")
            if (startResponse.session_id != null){
                sessionId = startResponse.session_id
            }
        }
        messageReceiveFragment.appendMessageToScreen(result)
    }

    fun initView() {
        vpContainer = findViewById(R.id.vp_contain)
        socketManager.addBrainDataListener(brainDataCallback)
        pagerSlidingTabStrip = findViewById(R.id.message_tabs)
        val listFragment = mutableListOf<Fragment>()
        messageReceiveFragment = MessageReceiveFragment()
        messageSendFragment = MessageSendFragment()
        listFragment.add(messageReceiveFragment)
        listFragment.add(messageSendFragment)

        val listTitles = listOf<String>("接受消息", "发送消息")
        var adapter = MessageAdapter(supportFragmentManager, listFragment, listTitles)
        vpContainer.adapter = adapter
        pagerSlidingTabStrip.setViewPager(vpContainer)

    }

    fun onClear(view: View) {
        messageReceiveFragment.clearScreen()
        messageSendFragment.clearScreen()
    }

    fun onConnect(view: View) {
        flowtimeBleManager.scanNearDeviceAndConnect(fun() {
            messageReceiveFragment.appendMessageToScreen("扫描设备成功")
            Logger.d("扫描设备成功")
        }, fun(mac: String) {
            flowtimeBleManager.addRawDataListener(rawListener)
            messageReceiveFragment.appendMessageToScreen("连接成功$mac")
            Logger.d("连接成功$mac")
            runOnUiThread {
                Toast.makeText(this@MainActivity, "设备连接成功", Toast.LENGTH_SHORT).show()
            }
        }) { msg ->
            messageReceiveFragment.appendMessageToScreen("连接失败")
            Logger.d("连接失败")
            runOnUiThread {
                Toast.makeText(this@MainActivity, "设备连接失败：${msg}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onDisconnet(view: View) {
        flowtimeBleManager.removeRawDataListener(rawListener)
        flowtimeBleManager.disConnect()
    }

    fun start(view: View) {
        if (!flowtimeBleManager.isConnected()) {
            Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
            return
        }
        var dataEntity = DataEntity()
        dataEntity.request_id = "${System.currentTimeMillis()}"
        dataEntity.command = "start"
        dataEntity.device_id = "A0"
        var json = Gson().toJson(dataEntity)
        messageSendFragment.appendMessageToScreen(json + "\r\n")
        socketManager.sendMessage(json)
    }

    fun process(view: View) {
        if (!flowtimeBleManager.isConnected()) {
            Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
            return
        }
        flowtimeBleManager.startBrainCollection()
    }

    fun finish(view: View) {
        if (!flowtimeBleManager.isConnected()) {
            Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
            return
        }
        flowtimeBleManager.stopBrainCollection()
        var dataEntity = DataEntity()
        dataEntity.command = "finish"
        dataEntity.request_id = "${System.currentTimeMillis()}"
        var json = Gson().toJson(dataEntity)
        messageSendFragment.appendMessageToScreen(json + "\r\n")
        socketManager.sendMessage(json)
        socketManager.connect()
    }

    fun restore(view:View){
        flowtimeBleManager.stopBrainCollection()
        socketManager.close()
        socketManager.connect {
            var dataEntity = DataEntity()
            dataEntity.request_id = "${System.currentTimeMillis()}"
            dataEntity.command = "restore"
            dataEntity.session_id = sessionId
            var json = Gson().toJson(dataEntity)
            messageSendFragment.appendMessageToScreen(json + "\r\n")
            socketManager.sendMessage(json)
        }
    }


    /**
     * Android6.0 auth
     */
    fun initPermission() {
        val needPermission = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val needRequestPermissions = ArrayList<String>()
        for (i in needPermission.indices) {
            if (ActivityCompat.checkSelfPermission(this, needPermission[i]) != PackageManager.PERMISSION_GRANTED) {
                needRequestPermissions.add(needPermission[i])
            }
        }
        if (needRequestPermissions.size != 0) {
            val permissions = arrayOfNulls<String>(needRequestPermissions.size)
            for (i in needRequestPermissions.indices) {
                permissions[i] = needRequestPermissions[i]
            }
            ActivityCompat.requestPermissions(this@MainActivity, permissions, 1)
        }
    }

    var socketBuffer = ArrayList<Int>()
    var rawListener = fun(bytes: ByteArray) {
        Logger.d("brain data is " + Arrays.toString(bytes))
        for (byte in bytes) {
            socketBuffer.add(ConvertUtil.converUnchart(byte))
            if (socketBuffer.size >= 600) {
                var dataEntity = DataEntity()
                dataEntity.command = "process"
                dataEntity.request_id = "${System.currentTimeMillis()}"
                dataEntity.data = socketBuffer.toIntArray()
                var json = Gson().toJson(dataEntity)
                messageSendFragment.appendMessageToScreen(json + "\r\n")
                socketManager.sendMessage(json)
                socketBuffer.clear()
            }
        }
    }

    override fun onDestroy() {
        socketManager.close()
        flowtimeBleManager.stopBrainCollection()
        flowtimeBleManager.removeRawDataListener(rawListener)
        socketManager.removeBrainDataListener(brainDataCallback)
        super.onDestroy()
    }

}
