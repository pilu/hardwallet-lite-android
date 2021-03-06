package im.status.hardwallet_lite_android.app;

import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import im.status.hardwallet_lite_android.demo.R;
import im.status.hardwallet_lite_android.io.CardChannel;
import im.status.hardwallet_lite_android.io.CardManager;
import im.status.hardwallet_lite_android.io.OnCardConnectedListener;
import im.status.hardwallet_lite_android.wallet.*;
import org.spongycastle.util.encoders.Hex;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = "MainActivity";

  private NfcAdapter nfcAdapter;
  private CardManager cardManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    nfcAdapter = NfcAdapter.getDefaultAdapter(this);
    cardManager = new CardManager();

    cardManager.setOnCardConnectedListener(new OnCardConnectedListener() {
      @Override
      public void onConnected(CardChannel cardChannel) {
        try {
          // Applet-specific code
          WalletAppletCommandSet cmdSet = new WalletAppletCommandSet(cardChannel);

          Log.i(TAG, "Applet selection successful");

          // First thing to do is selecting the applet on the card.
          ApplicationInfo info = new ApplicationInfo(cmdSet.select().checkOK().getData());

          // If the card is not initialized, the INIT apdu must be sent. The actual PIN, PUK and pairing password values
          // can be either generated or chosen by the user. Using fixed values is highly discouraged.
          if (!info.isInitializedCard()) {
            Log.i(TAG, "Initializing card with test secrets");
            cmdSet.init("000000", "123456789012", "WalletAppletTest").checkOK();
            info = new ApplicationInfo(cmdSet.select().checkOK().getData());
          }

          Log.i(TAG, "Instance UID: " + Hex.toHexString(info.getInstanceUID()));
          Log.i(TAG, "Secure channel public key: " + Hex.toHexString(info.getSecureChannelPubKey()));
          Log.i(TAG, "Application version: " + info.getAppVersionString());
          Log.i(TAG, "Free pairing slots: " + info.getFreePairingSlots());
          if (info.hasMasterKey()) {
            Log.i(TAG, "Key UID: " + Hex.toHexString(info.getKeyUID()));
          } else {
            Log.i(TAG, "The card has no master key");
          }

          // In real projects, the pairing key should be saved and used for all new sessions.
          cmdSet.autoPair("WalletAppletTest");
          Pairing pairing = cmdSet.getPairing();

          // Never log the pairing key in a real application!
          Log.i(TAG, "Pairing with card is done.");
          Log.i(TAG, "Pairing index: " + pairing.getPairingIndex());
          Log.i(TAG, "Pairing key: " + Hex.toHexString(pairing.getPairingKey()));

          // Opening a Secure Channel is needed for all other applet commands
          cmdSet.autoOpenSecureChannel();

          Log.i(TAG, "Secure channel opened. Getting applet status.");

          // We send a GET STATUS command, which does not require PIN authentication
          ApplicationStatus status = new ApplicationStatus(cmdSet.getStatus(WalletAppletCommandSet.GET_STATUS_P1_APPLICATION).checkOK().getData());

          Log.i(TAG, "PIN retry counter: " + status.getPINRetryCount());
          Log.i(TAG, "PUK retry counter: " + status.getPUKRetryCount());
          Log.i(TAG, "Has master key: " + status.hasMasterKey());

          // PIN authentication allows execution of privileged commands
          cmdSet.verifyPIN("000000").checkOK();

          Log.i(TAG, "Pin Verified.");

          // If the card has no keys, we generate a new set. Keys can also be loaded on the card starting from a binary
          // seed generated from a mnemonic phrase. The card can also generate mnemonics.
          if (!status.hasMasterKey()) {
            cmdSet.generateKey();
          }

          // Key derivation is needed to select the desired key. The derived key remains current until a new derive
          // command is sent (it is not lost on power loss). With GET STATUS one can retrieve the current path.
          cmdSet.deriveKey("m/44'/0'/0'/0/0").checkOK();

          Log.i(TAG, "Derived m/44'/0'/0'/0/0");

          byte[] hash = "thiscouldbeahashintheorysoitisok".getBytes();

          RecoverableSignature signature = new RecoverableSignature(hash, cmdSet.sign(hash).checkOK().getData());

          Log.i(TAG, "Signed hash: " + Hex.toHexString(hash));
          Log.i(TAG, "Recovery ID: " + signature.getRecId());
          Log.i(TAG, "R: " + Hex.toHexString(signature.getR()));
          Log.i(TAG, "S: " + Hex.toHexString(signature.getS()));

          // Cleanup, in a real application you would not unpair and instead keep the pairing key for successive interactions.
          // We also remove all other pairings so that we do not fill all slots with failing runs. Again in real application
          // this would be a very bad idea to do.
          cmdSet.unpairOthers();
          cmdSet.autoUnpair();

          Log.i(TAG, "Unpaired.");

        } catch (Exception e) {
          Log.e(TAG, e.getMessage());
        }

      }
    });
    cardManager.start();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (nfcAdapter != null) {
      nfcAdapter.enableReaderMode(this, this.cardManager, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (nfcAdapter != null) {
      nfcAdapter.disableReaderMode(this);
    }
  }
}
