package br.usp.rpcar;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;
import android.app.ProgressDialog;

import java.util.UUID;
import java.io.IOException;

public class ButtonManager extends AppCompatActivity {

    String address = null;
    String deviceName = null;

    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    private boolean connectionLost = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private ProgressDialog progress;
    private double last_x = 0;
    private double last_y = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_button);

        Intent newint = getIntent();
        deviceName = newint.getStringExtra(DevicesManager.EXTRA_NAME);
        address = newint.getStringExtra(DevicesManager.EXTRA_ADDRESS);

        TextView statusView = (TextView) findViewById(R.id.status);

        final View selectionArea = (View) findViewById(R.id.selectionArea);

        final View buttonFrame = (View) findViewById(R.id.roundButtonFrame);

        final View roundButton = (View) findViewById(R.id.roundButton);

        final View touchFeedback = (View) findViewById(R.id.touchFeedback);

        statusView.setText("Connecting to " + deviceName);

        new ConnectBT().execute();

        selectionArea.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    pressed(buttonFrame, roundButton, touchFeedback, event);

                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    released(buttonFrame, roundButton, touchFeedback, event);

                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    moved(buttonFrame, roundButton, touchFeedback, event);
                }
                return false;
            }
        });

    }

    private void pressed(View buttonFrame, View roundButton, View touchFeedback, MotionEvent event) {
        Point point = calcPoint(buttonFrame, roundButton, event);
        changeTouchFeedbackPosition(roundButton, touchFeedback, point);
        double x = point.getX();
        double y = point.getY();
        send(buildMessage("1", x, y));
        last_x = x;
        last_y = y;
    }

    private void released(View buttonFrame, View roundButton, View touchFeedback, MotionEvent event) {
        Point p = calcPoint(buttonFrame, roundButton, event);
        touchFeedback.setAlpha(0);
        double x = p.getX();
        double y = p.getY();
        send(buildMessage("0", x, y));
        last_x = x;
        last_y = y;
    }

    private void moved(View buttonFrame, View roundButton, View touchFeedback, MotionEvent event) {
        Point point = calcPoint(buttonFrame, roundButton, event);
        changeTouchFeedbackPosition(roundButton, touchFeedback, point);
        double x = point.getX();
        double y = point.getY();
        //has x or y changed?
        if ((x != last_x) || (y != last_y)) {
            send(buildMessage("2", x, y));
            last_x = x;
            last_y = y;
        }
    }

    private Point calcPoint(View buttonFrame, View roundButton, MotionEvent event) {
        int topPadding = buttonFrame.getTop();
        int leftPadding = buttonFrame.getLeft();
        double x = (event.getX() - leftPadding - (roundButton.getWidth() / 2)) / (roundButton.getWidth() / 2);
        double y = (event.getY() - topPadding - (roundButton.getHeight() / 2)) / (roundButton.getHeight() /2) * -1;
        double length = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
        if (length > 1) {
            x /= length;
            y /= length;
        }
        x = (double)Math.round(x * 100d) / 100d;
        y = (double)Math.round(y * 100d) / 100d;
        return new Point(x, y);
    }

    private void changeTouchFeedbackPosition(View roundButton, View touchFeedback, Point point) {
        int dimension = roundButton.getHeight() / 3;
        double transformationFactor = roundButton.getHeight() - touchFeedback.getHeight();
        int top = roundButton.getTop() - (int) (transformationFactor * (point.getY() - 1.0) / 2.0);
        int left = roundButton.getLeft() + (int) (transformationFactor * (point.getX() + 1.0) / 2.0);

        touchFeedback.setLeft(left);
        touchFeedback.setRight(left + dimension);
        touchFeedback.setTop(top);
        touchFeedback.setBottom(top + dimension);
        touchFeedback.setAlpha(1);
    }

    private String buildMessage(String operation, double x, double y) {
        return (operation + "," + String.valueOf(x) + "," + String.valueOf(y) + "\n");
    }

    private void send(String message) {
        if (btSocket!=null) {
            try {
                btSocket.getOutputStream().write(message.getBytes());
            } catch (IOException e) {
                msg("Error : " + e.getMessage());
                if(e.getMessage().contains("Broken pipe")) Disconnect();
            }
        } else {
            msg("Error : btSocket == null");
        }
    }

    private void Disconnect() {
        if (btSocket!=null) {
            try {
                isBtConnected = false;
                btSocket.close();
            } catch (IOException e) {
                msg("Error");
            }
        }
        Toast.makeText(getApplicationContext(),"Disconnected",Toast.LENGTH_LONG).show();
        finish();
    }

    private void msg(String message) {
        TextView statusView = (TextView)findViewById(R.id.status);
        statusView.setText(message);
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;

        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(ButtonManager.this, "Connecting", "Please wait...");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) { //while the progress dialog is shown, the connection is done in background
            try {
                if (btSocket == null || !isBtConnected) {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice myBluetoothRemoteDevice = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = myBluetoothRemoteDevice.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                }
            } catch (IOException e) {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) { //after the doInBackground, it checks if everything went fine
            super.onPostExecute(result);

            if (!ConnectSuccess) {
                Toast.makeText(getApplicationContext(), "Failed to connect", Toast.LENGTH_LONG).show();
                finish();
            } else {
                msg("Connected to " + deviceName);
                isBtConnected = true;
                // start the connection monitor
                new MonitorConnection().execute();
            }
            progress.dismiss();
        }
    }

    private class MonitorConnection extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... devices) {
            while (!connectionLost) {
                try {
                    //read from the buffer, when this errors the connection is lost
                    // this was the only reliable way I found of monitoring the connection
                    // .isConnected didnt work
                    // BluetoothDevice.ACTION_ACL_DISCONNECTED didnt fire
                    btSocket.getInputStream().read();
                } catch (IOException e) {
                    connectionLost = true;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            // if the bt is still connected, the connection must have been lost
            if (isBtConnected) {
                try {
                    isBtConnected = false;
                    btSocket.close();
                } catch (IOException e) {
                    // nothing doing, we are ending anyway!
                }
                Toast.makeText(getApplicationContext(), "Connection lost", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        Disconnect();
    }
}
