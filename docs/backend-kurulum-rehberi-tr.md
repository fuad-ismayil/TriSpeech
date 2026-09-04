# TriSpeech backend kurulum rehberi (Render)

Bu rehber geliştirme önizlemesi içindir. Amaç, TokenMix API anahtarlarını APK'nin içine koymadan GPT-4o Mini çevrimiçi transkripsiyonu test etmektir.

## Sistem nasıl çalışır?

```text
Android uygulaması
  -> ses dosyasını TriSpeech backend'ine yükler
  -> backend gizli TokenMix anahtarlarından birini seçer
  -> backend sesi TokenMix /v1/audio/transcriptions adresine gönderir
  -> sonuç backend üzerinden uygulamaya döner
```

Telefon TokenMix anahtarını hiçbir zaman almaz. APK'de yalnızca backend'in HTTPS adresi bulunur.

## 1. Gereken hesaplar

Şunlara ihtiyacın var:

1. GitHub hesabı.
2. Render hesabı: https://render.com
3. TokenMix hesabı ve en az bir API anahtarı.

Daha önce mesajlarda veya kaynak kodunda paylaşılan anahtarları yalnızca geçici özel test için kullan. Herkese açık APK yayımlamadan önce bunları iptal edip yeni anahtar oluştur.

## 2. Projeyi özel GitHub deposuna yükle

Proje klasörü şu anda Git deposu değildir. GitHub'da yeni bir **Private** repository oluştur. Repository oluştururken README, `.gitignore` veya license ekleme; depo boş olsun.

PowerShell'i `C:\Users\Fuad\Desktop\TriSpeech` klasöründe aç ve çalıştır:

```powershell
git init
git add .
git commit -m "TriSpeech development preview"
git branch -M main
git remote add origin https://github.com/KULLANICI_ADIN/REPOSITORY_ADIN.git
git push -u origin main
```

`KULLANICI_ADIN` ve `REPOSITORY_ADIN` bölümlerini kendine göre değiştir.

Göndermeden önce GitHub'daki dosya listesinde şunların **bulunmadığını** kontrol et:

- `tokenmix.properties`
- `backend/.env`
- `local.properties`
- herhangi bir `.jks` veya `.keystore` dosyası
- `backend/node_modules`

Bu yollar mevcut `.gitignore` tarafından engellenmektedir.

## 3. Render Blueprint oluştur

1. Render'a GitHub hesabınla giriş yap.
2. Dashboard'da **New +** düğmesine bas.
3. **Blueprint** seç.
4. TriSpeech GitHub repository'sini bağla.
5. Render kökteki `render.yaml` dosyasını bulacaktır.
6. Blueprint adını örneğin `trispeech-preview` yap.
7. Ortam değişkenleri istendiğinde gerçek API anahtarlarını aşağıdaki alanlara gir:
   - `TOKENMIX_KEY_1`
   - `TOKENMIX_KEY_2`
8. **Apply** veya **Deploy Blueprint** düğmesine bas.

Anahtar değerlerini GitHub'a, `render.yaml` dosyasına veya Android Gradle ayarına yazma. Değerler yalnızca Render'ın secret/environment ekranında bulunmalıdır.

## 4. Daha fazla API anahtarı ekle

Backend'de sabit bir anahtar sayısı sınırı yoktur. Render'da oluşturulan servisi aç:

1. **Environment** bölümüne gir.
2. **Add Environment Variable** seç.
3. Şu sırayla yeni değişkenler ekle:
   - `TOKENMIX_KEY_3`
   - `TOKENMIX_KEY_4`
   - `TOKENMIX_KEY_5`
   - gerektiği kadar devam et
4. Kaydet ve servisin yeniden deploy edilmesini bekle.

Sayıların aralıksız olması şart değildir. Örneğin `TOKENMIX_KEY_1`, `TOKENMIX_KEY_4` ve `TOKENMIX_KEY_20` birlikte çalışır. Küçük sayı daha yüksek önceliğe sahiptir. Aynı anahtar yanlışlıkla iki değişkene yazılırsa backend tekrarını yok sayar.

## 5. Backend'in açıldığını kontrol et

Deploy tamamlanınca Render sana buna benzer bir adres verir:

```text
https://trispeech-preview-backend.onrender.com
```

Tarayıcıda sonuna `/healthz` ekleyerek aç:

```text
https://SENIN-SERVISIN.onrender.com/healthz
```

Şunu görmelisin:

```json
{"ok":true}
```

Render Free servis 15 dakika kullanılmadığında uyuyabilir. İlk açılışın yaklaşık bir dakika sürmesi normaldir.

## 6. Android APK'yi backend adresiyle derle

PowerShell'i tekrar proje klasöründe aç:

```powershell
.\gradlew.bat assembleDebug -PTRISPEECH_BACKEND_URL=https://SENIN-SERVISIN.onrender.com
```

Adresin sonunda `/` olması gerekmez. Derlenen dosya:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Telefon USB debugging ile bağlıysa kurabilirsin:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

İstersen APK dosyasını telefona kopyalayıp elle de kurabilirsin.

## 7. GPT-4o Mini testi

1. TriSpeech'i aç.
2. Yöntem seçiminden **GPT-4o Mini Online** seç.
3. Bulut işleme açıklamasını kabul et.
4. Önce 5-10 saniyelik, gizli olmayan kısa bir kayıt kullan.
5. Kaydı durdur ve sonucu bekle.
6. Render Dashboard > Logs bölümünde isteklerin geldiğini kontrol et.

Loglarda anahtarın kendisi görünmez. Yalnızca `tokenmix-key-1` gibi alias görünür.

## 8. Fallback nasıl çalışır?

Backend anahtarları sayı sırasıyla dener.

- `401`: anahtar geçersizdir; kapatılır ve hemen sıradaki denenir.
- `403`: anahtar yetkisizdir; kapatılır ve hemen sıradaki denenir.
- Anahtara özel `429`: anahtar 15 dakikalık beklemeye alınır ve hemen sıradaki denenir.
- Hesabın tamamına ait `429`: deneme durur; aynı hesaptaki diğer anahtarların kotayı aşması muhtemeldir.
- Timeout, bağlantı kopması veya belirsiz sunucu sonucu: başka anahtara geçilmez. İlk isteğin TokenMix tarafından kabul edilmiş ve ücretlendirilmiş olma ihtimali vardır.
- Hatalı dosya/format gibi normal `4xx`: başka anahtar denenmez; anahtar değiştirmek dosya hatasını çözmez.

Bu sayede kesin biçimde bozuk veya kotası dolmuş bir anahtarda tüm yapılandırılmış anahtarlar sırayla kullanılabilir; belirsiz durumlarda aynı sesin iki kez ücretlendirilmesi engellenir.

## 9. Yerel bilgisayarda backend çalıştırma

Render kullanmadan önce yerel test yapmak istersen:

1. `backend/.env.example` dosyasının kopyasını `backend/.env` adıyla oluştur.
2. `.env` içindeki `TOKENMIX_KEY_1=` satırının karşısına anahtarını yaz.
3. PowerShell'de:

```powershell
cd backend
npm install
npm run dev
```

Android emülatörü varsayılan olarak `http://10.0.2.2:8080` adresini kullanır. Yerel HTTP yalnızca debug APK'de açıktır; release APK her zaman HTTPS ister.

## 10. Güvenlik ve masraf uyarısı

Bu geliştirme backend'ini herkese açık biçimde dağıtma. Reklam doğrulaması ve App Check şu anda kapalı olduğu için saldırgan yeni kurulum kimlikleri oluşturabilir.

- Render URL'sini yalnızca küçük bir test grubuyla paylaş.
- TokenMix hesabında mümkünse sert harcama/kota sınırı belirle.
- Render'daki `PROVIDER_MAX_CONCURRENCY=2` değerini test sırasında yükseltme.
- `PROVIDER_DAILY_BUDGET_USD=1` yalnızca backend'in bellek içi güvenlik sayacıdır; Render yeniden başladığında sıfırlanabilir. TokenMix tarafındaki limit daha önemlidir.
- Sorun olursa Render Environment bölümünde `ONLINE_SERVICE_ENABLED=false` yaparak çevrimiçi sistemi kapatabilirsin.

## Yaygın hatalar

### `CLEARTEXT communication ... not permitted`

Yerel emülatör için debug APK kullandığından emin ol. Render bağlantısı HTTPS olmalıdır.

### `Online transcription is temporarily unavailable`

Render Environment bölümünde en az bir dolu `TOKENMIX_KEY_N` bulunduğunu ve deploy'un tamamlandığını kontrol et.

### `Provider rejected request (401)`

Anahtar geçersiz veya iptal edilmiştir. Render'a yeni bir numaralı anahtar ekleyip yeniden deploy et.

### `413` veya `Invalid upload size`

Dosya 25 MiB sınırını aşmıştır. Daha kısa dosya seç veya Offline Whisper kullan.

### İlk istek çok geç geliyor

Render Free servisin uyku durumundan kalkması yaklaşık bir dakika sürebilir. `/healthz` adresini açıp servis uyandıktan sonra tekrar dene.
