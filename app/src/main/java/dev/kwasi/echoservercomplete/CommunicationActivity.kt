package dev.kwasi.echoservercomplete

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.kwasi.echoservercomplete.chatlist.ChatListAdapter
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.network.Client
import dev.kwasi.echoservercomplete.network.NetworkMessageInterface
import dev.kwasi.echoservercomplete.network.Server
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapter
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapterInterface
import dev.kwasi.echoservercomplete.peerlist.AttendeeListAdapter
import dev.kwasi.echoservercomplete.peerlist.AttendeeListAdapterInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectManager
import android.util.Log
import android.widget.TextView
import java.security.MessageDigest
import kotlin.text.Charsets.UTF_8
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.SecretKey
import javax.crypto.Cipher
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

fun ByteArray.toHex() = joinToString(separator = "") { byte-> "%02x".format(byte) }
fun getFirstNChars(str: String, n:Int) = str.substring(0,n)

class CommunicationActivity : AppCompatActivity(), WifiDirectInterface, PeerListAdapterInterface, AttendeeListAdapterInterface, NetworkMessageInterface {
    private var wfdManager: WifiDirectManager? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var peerListAdapter:PeerListAdapter? = null
    private var chatListAdapter:ChatListAdapter? = null
    private var attendeeListAdapter:AttendeeListAdapter? = null

    private var wfdAdapterEnabled = false
    private var wfdHasConnection = false
    private var classStarted = false
    private var hasDevices = false
    private var server: Server? = null
    private var client: Client? = null
    private var deviceIp: String = ""

    private val studentIDList = arrayOf(
        816111111,
        816222222,
        816333333,
        816444444,
        816555555,
        816666666,
        816777777,
        816888888,
        816999999,
        816117992
    )

    private fun hashStrSha256(str: String): String{
        val algorithm = "SHA-256"
        val hashedString = MessageDigest.getInstance(algorithm).digest(str.toByteArray(UTF_8))
        return hashedString.toHex();
    }

    private fun generateAESKey(seed: String): SecretKeySpec {
        val first32Chars = getFirstNChars(seed,32)
        val secretKey = SecretKeySpec(first32Chars.toByteArray(), "AES")
        return secretKey
    }

    private fun generateIV(seed: String): IvParameterSpec {
        val first16Chars = getFirstNChars(seed, 16)
        return IvParameterSpec(first16Chars.toByteArray())
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encryptMessage(plaintext: String, aesKey:SecretKey, aesIv: IvParameterSpec):String{
        val plainTextByteArr = plaintext.toByteArray()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
        val encrypt = cipher.doFinal(plainTextByteArr)
        return Base64.Default.encode(encrypt)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decryptMessage(encryptedText: String, aesKey:SecretKey, aesIv: IvParameterSpec):String{
        val textToDecrypt = Base64.Default.decode(encryptedText)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.DECRYPT_MODE, aesKey,aesIv)
        val decrypt = cipher.doFinal(textToDecrypt)
        return String(decrypt)
    }

    private fun generateR(): Int
    {
        return Random.nextInt()
    }

    private fun getEncryption(R: String, seed: String): String
    {
        val strongSeed = hashStrSha256(seed)
        val aesKey = generateAESKey(strongSeed)
        val aesIV = generateIV(strongSeed)

        Log.e("CA", "Random number: $R")
        Log.e("CA", seed)

        return encryptMessage(R, aesKey, aesIV)
    }

    private fun verifyR(R: String, id: String, encryption: String): Boolean
    {
        val seed = id
        val strongSeed = hashStrSha256(seed)
        val aesKey = generateAESKey(strongSeed)
        val aesIV = generateIV(strongSeed)

        val decryption = decryptMessage(encryption, aesKey, aesIV)

        return (R == decryption)
    }

    private fun encryptTest()
    {
        val R = generateR()
        val encryption = getEncryption(R.toString(), studentIDList[9].toString())

        Log.e("CA", "Encrypted message: $encryption")

        val res = verifyR(R.toString(), studentIDList[9].toString(), encryption)

        if(res)
        {
            Log.e("CA", "Decryption successful")
        }
        else
        {
            Log.e("CA", "Decryption unsuccessful")
        }
    }


//    fun encryptTest()
//    {
//        val seed = studentIDList[9].toString()
//        val strongSeed = hashStrSha256(seed)
//        val aesKey = generateAESKey(strongSeed)
//        val aesIV = generateIV(strongSeed)
//        val R = generateR()
//        Log.e("CA", "Random number: $R")
//        Log.e("CA", seed)
//        val cyphertext = encryptMessage(R, aesKey, aesIV)
//        val decryptedCypherText = decryptMessage(cyphertext, aesKey, aesIV)
//        Log.e("CA", "Encryption: $cyphertext")
//        Log.e("CA", "Decrypted: $decryptedCypherText")
//
//        if(R == decryptedCypherText)
//        {
//            Log.e("CA", "Decryption successful")
//        }
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_communication)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        wfdManager = WifiDirectManager(manager, channel, this)

        peerListAdapter = PeerListAdapter(this)
        val rvPeerList: RecyclerView= findViewById(R.id.rvPeerListing)
        rvPeerList.adapter = peerListAdapter
        rvPeerList.layoutManager = LinearLayoutManager(this)

        attendeeListAdapter = AttendeeListAdapter(this)
        val rvAttendeeList: RecyclerView= findViewById(R.id.attendanceList)
        rvAttendeeList.adapter = attendeeListAdapter
        rvAttendeeList.layoutManager = LinearLayoutManager(this)

        chatListAdapter = ChatListAdapter()
        val rvChatList: RecyclerView = findViewById(R.id.studentChat)
        rvChatList.adapter = chatListAdapter
        rvChatList.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        wfdManager?.also {
            registerReceiver(it, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        wfdManager?.also {
            unregisterReceiver(it)
        }
    }
    fun createGroup(view: View) {
        wfdManager?.createGroup()
        classStarted = true
//        Log.e("CA", studentIDList.contentToString())
//        encryptTest()
//        val idTest: Boolean = lookupID(816117929)
//
//        if(idTest)
//        {
//            Log.e("CA", "Student ID found")
//        }
//        else
//        {
//            Log.e("CA", "Student ID not found")
//        }

        attendeeListAdapter?.addAttendee("816000000")
        attendeeListAdapter?.addAttendee("816111111")
        attendeeListAdapter?.addAttendee("816222222")
        attendeeListAdapter?.addAttendee("816222222")
        attendeeListAdapter?.addAttendee("816333333")
        attendeeListAdapter?.addAttendee("816444444")

        updateUI()
    }

    fun lookupID(id: Int): Boolean
    {
        for(s in studentIDList)
        {
            if(id == s) return true
        }

        return false
    }

    fun endClass(view: View) {
        server?.close()
        classStarted = false
        wfdHasConnection = false
        updateUI()
    }

    fun discoverNearbyPeers(view: View) {
        wfdManager?.discoverPeers()
    }

    private fun updateUI(){
        //The rules for updating the UI are as follows:
        // IF the WFD adapter is NOT enabled then
        //      Show UI that says turn on the wifi adapter
        // ELSE IF there is NO WFD connection then i need to show a view that allows the user to either
        // 1) create a group with them as the group owner OR
        // 2) discover nearby groups
        // ELSE IF there are nearby groups found, i need to show them in a list
        // ELSE IF i have a WFD connection i need to show a chat interface where i can send/receive messages
        val wfdAdapterErrorView:ConstraintLayout = findViewById(R.id.clWfdAdapterDisabled)
        wfdAdapterErrorView.visibility = if (!wfdAdapterEnabled) View.VISIBLE else View.GONE

        val wfdNoConnectionView:ConstraintLayout = findViewById(R.id.clNoWifiDirectConnection)
        wfdNoConnectionView.visibility = if (wfdAdapterEnabled && !wfdHasConnection) View.VISIBLE else View.GONE

        val rvPeerList: RecyclerView= findViewById(R.id.rvPeerListing)
        rvPeerList.visibility = if (wfdAdapterEnabled && !wfdHasConnection && hasDevices) View.VISIBLE else View.GONE

        val wfdConnectedView:ConstraintLayout = findViewById(R.id.clHasConnection)
        wfdConnectedView.visibility = if(wfdHasConnection)View.VISIBLE else View.GONE

        val classStartedView:ConstraintLayout = findViewById(R.id.classStarted)

        if(classStarted)
        {
            classStartedView.visibility = View.VISIBLE
            wfdNoConnectionView.visibility = View.GONE
        }
        else
        {
            classStartedView.visibility = View.GONE
        }

        val networkName: TextView = findViewById(R.id.tvNetworkName)
        val networkNameString: String = wfdManager?.groupInfo?.networkName ?: ""
        networkName.text = "Class Network: $networkNameString"

        Log.e("CA", "Class Network: $networkNameString")

        val networkPassword: TextView = findViewById(R.id.tvNetworkPassword)
        val networkPasswordString: String = wfdManager?.groupInfo?.passphrase ?: ""
        networkPassword.text = "Network Password: $networkPasswordString"

        Log.e("CA", "Network Password: $networkPasswordString")
    }

    fun sendMessage(view: View) {
        val enterMessage:EditText = findViewById(R.id.enterMessage)
        val enterString = enterMessage.text.toString()

        val seed = "816117992"
        val encryptedMessage = getEncryption(enterString, seed)

        Log.e("CA", enterString)
        Log.e("CA", encryptedMessage)

        val decryptedMessage = decryptMessage(encryptedMessage, generateAESKey(hashStrSha256(seed)), generateIV(hashStrSha256(seed)))

        Log.e("CA", decryptedMessage)

        val content = ContentModel(enterString, deviceIp)
        enterMessage.text.clear()
        client?.sendMessage(content)
        chatListAdapter?.addItemToEnd(content)

    }

    override fun onWiFiDirectStateChanged(isEnabled: Boolean) {
        wfdAdapterEnabled = isEnabled
        var text = "There was a state change in the WiFi Direct. Currently it is "
        text = if (isEnabled){
            "$text enabled!"
        } else {
            "$text disabled! Try turning on the WiFi adapter"
        }

        val toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
        toast.show()
        updateUI()
    }

    override fun onPeerListUpdated(deviceList: Collection<WifiP2pDevice>) {
        val toast = Toast.makeText(this, "Updated listing of nearby WiFi Direct devices", Toast.LENGTH_SHORT)
        toast.show()
        hasDevices = deviceList.isNotEmpty()
        peerListAdapter?.updateList(deviceList)
        updateUI()
    }

    override fun onGroupStatusChanged(groupInfo: WifiP2pGroup?) {
        val text = if (groupInfo == null){
            "Group is not formed"
        } else {
            "Group has been formed"
        }
        val toast = Toast.makeText(this, text , Toast.LENGTH_SHORT)
        toast.show()
        wfdHasConnection = groupInfo != null

        if (groupInfo == null){
            server?.close()
            client?.close()
        } else if (groupInfo.isGroupOwner && server == null){
            server = Server(this)
            deviceIp = "192.168.49.1"
        } else if (!groupInfo.isGroupOwner && client == null) {
            client = Client(this)
            deviceIp = client!!.ip

            Log.e("CA", "A student joined the group")
        }

    }

    override fun onDeviceStatusChanged(thisDevice: WifiP2pDevice) {
        val toast = Toast.makeText(this, "Device parameters have been updated" , Toast.LENGTH_SHORT)
        toast.show()
    }

    override fun onPeerClicked(peer: WifiP2pDevice) {
        wfdManager?.connectToPeer(peer)
    }

    private fun switchStudentChat()
    {

    }

    override fun onAttendeeClicked(attendee: String)
    {
        switchStudentChat()
    }

    private fun onAttendeeClicked()
    {
        Log.e("CA","Attendee clicked")
    }



    override fun onContent(content: ContentModel) {
        runOnUiThread{
            chatListAdapter?.addItemToEnd(content)
        }
    }

}