package example.com.kii.mqtt_sample;

import android.os.AsyncTask;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.Toast;

import com.kii.cloud.storage.Kii;
import com.kii.cloud.storage.KiiUser;
import com.kii.cloud.storage.exception.app.AppException;
import com.kii.cloud.storage.exception.app.BadRequestException;
import com.kii.cloud.storage.exception.app.ConflictException;
import com.kii.cloud.storage.exception.app.ForbiddenException;
import com.kii.cloud.storage.exception.app.NotFoundException;
import com.kii.cloud.storage.exception.app.UnauthorizedException;
import com.kii.cloud.storage.exception.app.UndefinedException;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jdeferred.DoneCallback;
import org.jdeferred.DonePipe;
import org.jdeferred.FailCallback;
import org.jdeferred.Promise;
import org.jdeferred.android.AndroidDeferredManager;
import org.jdeferred.android.DeferredAsyncTask;
import org.json.JSONObject;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "KiiSample";
    AndroidDeferredManager adm;

    @Override
    protected void onPause() {
        super.onPause();
        adm.getExecutorService().shutdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adm = new AndroidDeferredManager();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adm = new AndroidDeferredManager();
        Kii.initialize(this, "9ab34d8b", "7a950d78956ed39f3b0815f0f001b43b", Kii.Site.JP);
        setContentView(R.layout.activity_main);

        Button login = (Button) findViewById(R.id.buttonLogin);
        Button signup = (Button) findViewById(R.id.buttonSignUp);
        final EditText usernameEdit = (EditText) findViewById(R.id.userNameEdit);
        final EditText passwordEdit = (EditText) findViewById(R.id.passwordEdit);

        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String username = usernameEdit.getText().toString();
                String password = passwordEdit.getText().toString();
                process(loginTask(username, password)).then(new DoneCallback<Void>() {
                    @Override
                    public void onDone(Void result) {
                        Toast.makeText(MainActivity.this, "Everything is fine! Ready for receive push.", Toast.LENGTH_LONG).show();
                    }
                }).fail(new FailCallback<Throwable>() {
                    @Override
                    public void onFail(Throwable result) {
                        Log.v(TAG, "Chain failed:");
                        result.printStackTrace();
                        Toast.makeText(MainActivity.this, "Chain failed: " + result.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        signup.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                String username = usernameEdit.getText().toString();
                String password = passwordEdit.getText().toString();
                process(signUpTask(username, password)).then(new DoneCallback<Void>() {
                    @Override
                    public void onDone(Void result) {
                        Toast.makeText(MainActivity.this, "Everything is fine! Ready for receive push.", Toast.LENGTH_LONG).show();
                    }
                }).fail(new FailCallback<Throwable>() {
                    @Override
                    public void onFail(Throwable result) {
                        Log.v(TAG, "Chain failed:");
                        result.printStackTrace();
                        Toast.makeText(MainActivity.this, "Chain failed: " + result.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private Promise<Void, Throwable, Void> process(DeferredAsyncTask<Void, Void, KiiUser> userTask) {
        return (adm.when(userTask).then(new DonePipe<KiiUser, Pair<KiiUser,String>, Throwable, Void>() {
            @Override
            public Promise<Pair<KiiUser,String>, Throwable, Void> pipeDone(KiiUser result) {
                return adm.when(installPush(result));
            }
        }).then(new DonePipe<Pair<KiiUser,String>, JSONObject, Throwable, Void>() {
            @Override
            public Promise<JSONObject, Throwable, Void> pipeDone(Pair<KiiUser,String> result) {
                return adm.when(getEndpoint(result.first, result.second));
            }
        }).then(new DonePipe<JSONObject, Void, Throwable, Void>() {
            @Override
            public Promise<Void, Throwable, Void> pipeDone(JSONObject result) {
                return adm.when(mqttConnect(result));
            }
        }));
    }

    private DeferredAsyncTask<Void, Void, KiiUser> loginTask(final String username, final String password) {
        return new DeferredAsyncTask<Void, Void, KiiUser>() {
            @Override
            protected KiiUser doInBackgroundSafe(Void... params) throws Exception {
                KiiUser user = KiiUser.logIn(username, password);
                return user;
            }
        };
    }

    private DeferredAsyncTask<Void, Void, KiiUser> signUpTask(final String username, final String password) {
        return new DeferredAsyncTask<Void, Void, KiiUser>() {
            @Override
            protected KiiUser doInBackgroundSafe(Void... params) throws Exception {
                KiiUser user = KiiUser.createWithUsername(username);
                user.register(password);
                return user;
            }
        };
    }

    private DeferredAsyncTask<Void, Void, Pair<KiiUser, String>> installPush(final KiiUser user) {
        return new DeferredAsyncTask<Void, Void, Pair<KiiUser, String>>() {
            @Override
            protected Pair<KiiUser, String> doInBackgroundSafe(Void... params) throws Exception {
                OkHttpClient client = new OkHttpClient();
                String url = Kii.getBaseURL() + "/apps/" + Kii.getAppId() + "/installations";

                JSONObject bodyJSON = new JSONObject();
                bodyJSON.put("deviceType", "MQTT");
                String contentType = "application/vnd.kii.InstallationCreationRequest+json";
                RequestBody body = RequestBody.create(MediaType.parse(contentType), bodyJSON.toString());

                Request r = new Request.Builder().
                        url(url).
                        method("POST", body).
                        addHeader("Authorization", "Bearer " + user.getAccessToken()).
                        addHeader("X-Kii-Appid", Kii.getAppId()).
                        addHeader("X-kii-Appkey", Kii.getAppKey()).
                        build();
                Response response = client.newCall(r).execute();
                String respStr = response.body().string();
                Log.v(TAG, "respStr: " + respStr);
                JSONObject respJSON = new JSONObject(respStr);
                String installationID = respJSON.getString("installationID");
                return new Pair<>(user, installationID);
            }
        };
    }

    private DeferredAsyncTask<Void, Void, JSONObject> getEndpoint(final KiiUser user, final String installationID) {
        return new DeferredAsyncTask<Void, Void, JSONObject>() {
            @Override
            protected JSONObject doInBackgroundSafe(Void... params) throws Exception {
                OkHttpClient client = new OkHttpClient();
                String url = Kii.getBaseURL() + "/apps/" + Kii.getAppId() + "/installations/" + installationID + "/mqtt-endpoint";

                Request r = new Request.Builder().
                        url(url).
                        addHeader("Authorization", "Bearer " + user.getAccessToken()).
                        addHeader("X-Kii-Appid", Kii.getAppId()).
                        addHeader("X-kii-Appkey", Kii.getAppKey()).
                        build();
                Response response = client.newCall(r).execute();
                String respStr = response.body().string();
                Log.v(TAG, "respStr: " + respStr);
                JSONObject respJSON = new JSONObject(respStr);
                return respJSON;
            }
        };
    }

    private DeferredAsyncTask<Void, Void, Void> mqttConnect(final JSONObject endpoint) {
        return new DeferredAsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackgroundSafe(Void... params) throws Exception {
                // TOOD: implement it.
                Log.v(TAG, "mqttConnect: " + endpoint);
                String host = endpoint.getString("host");
                int port = endpoint.getInt("portTCP");
                String url = String.format("tcp://%s:%d", host, port);

                String username = endpoint.getString("username");
                String password = endpoint.getString("password");
                final String topic = endpoint.getString("mqttTopic");
                final MqttAndroidClient mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), url , topic);
                final MqttConnectOptions options = new MqttConnectOptions();
                options.setUserName(username);
                options.setPassword(password.toCharArray());

                mqttAndroidClient.setCallback(new MqttCallback() {
                    @Override
                    public void connectionLost(Throwable cause) {
                        Log.v(TAG, "connectionLost " + cause.getMessage());
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        Log.v(TAG, "messageArrived " + message.toString());
                        Toast.makeText(MainActivity.this, "messageArrived: " + message.toString(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        Log.v(TAG, "deliveryComplete ");
                    }
                });

                mqttAndroidClient.connect(options, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.v(TAG, "succeed to connect");
                        try {
                            mqttAndroidClient.subscribe(topic, 0);
                            Log.v(TAG, "succeed to subscribe");
                        } catch (MqttException e) {
                            Log.v(TAG, "failed to subscribe");
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.v(TAG, "failed to connect");
                        exception.printStackTrace();
                    }
                });

                return null;
            }
        };
    }
}
