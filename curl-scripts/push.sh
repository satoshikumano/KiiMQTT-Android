curl -v -X POST \
-H "X-Kii-AppID: ${APP_ID}" \
-H "X-Kii-AppKey: ${APP_KEY}" \
-H "Authorization:Bearer ${ADMIN_TOKEN}" \
-H "Accept:application/vnd.kii.SendPushMessageResponse+json" \
-H "Content-Type:application/vnd.kii.SendPushMessageRequest+json" \
"https://api-jp.kii.com/api/apps/${APP_ID}/users/${USER_ID}/push/messages" \
-d '{ "data": { "content-url": "https://some.nice.contents/message/100" }, "sendToDevelopment": true, "sendToProduction": true, "sendSender" : true, "sendWhen": true, "sendOrigin": false, "gcm": { "enabled": true }, "apns": { "enabled": true, "contentAvailable": true }, "mqtt": { "enabled": true } }'
