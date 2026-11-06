# 微信 8.0.78 roomdata 成员时间字段证据

来源：wechat8078_decoded/smali_classes*/so/b.smali；storage/z2.smali 将 field_roomdata 解析为 so.a，其成员列表为 so.b。

以下摘录仅含协议，无用户日志正文或群成员身份。

```smali
158: .method public final varargs op(I[Ljava/lang/Object;)I
159:     .locals 7
160:
161:     const/4 v0, 0x4
162:
163:     const/4 v1, 0x6
164:
165:     const/4 v2, 0x5
166:
167:     const/4 v3, 0x3
168:
169:     const/4 v4, 0x2
170:
171:     const/4 v5, 0x1
172:
173:     const/4 v6, 0x0
```

```smali
199:     iget-object p2, p0, Lso/b;->g:Ljava/lang/String;
200:
201:     if-eqz p2, :cond_2
202:
203:     invoke-virtual {p1, v0, p2}, Lzc6/f;->j(ILjava/lang/String;)V
204:
205:     :cond_2
206:     iget p2, p0, Lso/b;->h:I
207:
208:     invoke-virtual {p1, v2, p2}, Lzc6/f;->e(II)V
209:
210:     iget p2, p0, Lso/b;->i:I
211:
212:     invoke-virtual {p1, v1, p2}, Lzc6/f;->e(II)V
```

```smali
441:     move-result-object v2
442:
443:     invoke-virtual {v3, v0, v1, v2, v4}, Ln91/e;->d(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/Object;Z)V
444:
445:     const-string v1, "inviterUserName"
446:
447:     iget-object v2, p0, Lso/b;->g:Ljava/lang/String;
448:
449:     invoke-virtual {v3, v0, v1, v2, v4}, Ln91/e;->d(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/Object;Z)V
450:
451:     const-string v1, "addChatRoomScene"
452:
453:     iget v2, p0, Lso/b;->h:I
454:
455:     invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
456:
457:     move-result-object v2
458:
459:     invoke-virtual {v3, v0, v1, v2, v4}, Ln91/e;->d(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/Object;Z)V
460:
461:     const-string v1, "joinTime"
462:
463:     iget v2, p0, Lso/b;->i:I
464:
465:     invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
466:
467:     move-result-object v2
468:
469:     invoke-virtual {v3, v0, v1, v2, v4}, Ln91/e;->d(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/Object;Z)V
```

原生写入链路（storage/z2.smali）：
```smali
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-477-    iput-object v3, v2, Lso/b;->g:Ljava/lang/String;
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-478-
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-479-    if-eqz p5, :cond_2
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-480-
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-481-    iget v1, v0, Lpc5/a00;->u:I
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-482-
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali:483:    iput v1, v2, Lso/b;->h:I
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-484-
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-485-    iget v0, v0, Lpc5/a00;->v:I
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-486-
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali:487:    iput v0, v2, Lso/b;->i:I
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-488-
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-489-    goto :goto_0
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-490-
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-491-    :cond_2
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali:492:    iput v1, v2, Lso/b;->h:I
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali-493-
/sdcard/Download/wechat8078_decoded/smali_classes8/com/tencent/mm/storage/z2.smali:494:    iput v1, v2, Lso/b;->i:I
```
