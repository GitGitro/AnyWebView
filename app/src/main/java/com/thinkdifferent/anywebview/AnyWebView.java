package com.thinkdifferent.anywebview;

import android.content.pm.PackageInfo;
import android.os.Build;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static com.thinkdifferent.anywebview.XposedHelpersWraper.findAndHookConstructor;
import static com.thinkdifferent.anywebview.XposedHelpersWraper.findAndHookMethod;
import static com.thinkdifferent.anywebview.XposedHelpersWraper.findClass;
import static com.thinkdifferent.anywebview.XposedHelpersWraper.findConstructorBestMatch;
import static com.thinkdifferent.anywebview.XposedHelpersWraper.findFirstFieldByExactType;
import static com.thinkdifferent.anywebview.XposedHelpersWraper.log;

public class AnyWebView extends BaseXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        //services.jar
        if (lpparam.packageName.equals("android")) {
            Initialize initialize = new Initialize(lpparam).invoke();
            Class<?> classWebViewProviderInfo = initialize.getClassWebViewProviderInfo();
            Class<?> classWebViewProviderInfoArray = initialize.getClassWebViewProviderInfoArray();
            Class<?> classSystemImpl = initialize.getClassSystemImpl();
            Object webViewProviders = initialize.getWebViewProviders();
            Class<?> classWebViewUpdater = initialize.getClassWebViewUpdater();

            findAndHookConstructor(classSystemImpl, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Field mWebViewProviderPackages = findFirstFieldByExactType(classSystemImpl, classWebViewProviderInfoArray);
                        mWebViewProviderPackages.setAccessible(true);
                        mWebViewProviderPackages.set(param.thisObject, webViewProviders);
                        param.setResult(param.thisObject);
                    } catch (Throwable t) {
                        log(t);
                    }
                }
            });

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                findAndHookMethod(classWebViewUpdater, "validityResult", classWebViewProviderInfo, PackageInfo.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(/*VALIDITY_OK*/0);
                    }
                });
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                findAndHookMethod(classWebViewUpdater, "isValidProvider", classWebViewProviderInfo, PackageInfo.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(true);
                    }
                });
            }
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) {
    }

    private static class Initialize {
        private XC_LoadPackage.LoadPackageParam lpparam;
        private Class<?> classWebViewProviderInfo;
        private Class<?> classWebViewProviderInfoArray;
        private Class<?> classSystemImpl;
        private Object webViewProviders;
        private Class<?> classWebViewUpdater;

        public Initialize(XC_LoadPackage.LoadPackageParam lpparam) {
            this.lpparam = lpparam;
        }

        public Class<?> getClassWebViewProviderInfo() {
            return classWebViewProviderInfo;
        }

        public Class<?> getClassWebViewProviderInfoArray() {
            return classWebViewProviderInfoArray;
        }

        public Class<?> getClassSystemImpl() {
            return classSystemImpl;
        }

        public Object getWebViewProviders() {
            return webViewProviders;
        }

        public Class<?> getClassWebViewUpdater() {
            return classWebViewUpdater;
        }


        public Initialize invoke() {
            log("invoke init");
            classWebViewProviderInfo = findClass(
                    "android.webkit.WebViewProviderInfo", lpparam.classLoader);
            Constructor<?> constructorWebViewProviderInfo = findConstructorBestMatch(
                    classWebViewProviderInfo,/*packageName*/String.class,
                    /*description*/String.class,/*TAG_AVAILABILITY*/boolean.class,
                    /*TAG_FALLBACK*/boolean.class,/*readSignatures*/String[].class);

            classWebViewProviderInfoArray = findClass(
                    "[Landroid.webkit.WebViewProviderInfo;", lpparam.classLoader);
            classSystemImpl = findClass("com.android.server.webkit.SystemImpl", lpparam.classLoader);
            webViewProviders = Array.newInstance(classWebViewProviderInfo, 13);
            try {
                //https://github.com/LineageOS/android_external_chromium-webview
                //MIIFLQYJKoZIhvcNAQcCoIIFHjCCBRoCAQExCzAJBgUrDgMCGgUAMAsGCSqGSIb3DQEHAaCCA1owggNWMIICPqADAgECAgRQTixCMA0GCSqGSIb3DQEBBQUAMGwxEDAOBgNVBAYTB1Vua25vd24xEDAOBgNVBAgTB1Vua25vd24xEDAOBgNVBAcTB1Vua25vd24xEDAOBgNVBAoTB1Vua25vd24xEDAOBgNVBAsTB1Vua25vd24xEDAOBgNVBAMTB1Vua25vd24wIBcNMTIwOTEwMTgwNjU4WhgPMjExMjA5MTExODA2NThaMGwxEDAOBgNVBAYTB1Vua25vd24xEDAOBgNVBAgTB1Vua25vd24xEDAOBgNVBAcTB1Vua25vd24xEDAOBgNVBAoTB1Vua25vd24xEDAOBgNVBAsTB1Vua25vd24xEDAOBgNVBAMTB1Vua25vd24wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDEqIiNX9BpPBuo3Th+u6wRoCeU/EjOS3pnqcc6xUpxBVzB9EJURVl0JEIUp1J4aK07El+YPaa6vtbGIEg6jdK/aSNiXRqCctU1+iUkAk6mV8meTsaajOCEtaDtxKCAPVptK7ADVHtVhGGzUyeYjh1De5d5UXF6earRiqEvX3GUzs4AyZ8OUe1W8B9FbzrkGKhjeDZIm4Sc64UaMAmEvHnt7yR4CmyeAy/aEhXVyfjcfEdnpaHJRI0MGHfPyWg1ZemtKKAslxQw2AS4WSi7fTpr8FUtG1WgzSy1DeG30mU6HAVK8E4KQD0UYXhGtVfmdRQjpDTRkS7c65E1L9NX3sCvAgMBAAEwDQYJKoZIhvcNAQEFBQADggEBAKKGhDGUjJiq+L3qdBf5EOjZgbjvh9OLbc1PasZtXuEtiwYwBFvpZXLZAcOsQV0QQiE4DvY8CG5PQ3Vi3icet+Mx8cbIe7QzSopHL0hm33pQr79GUtHETGinGbfMR0et3cEg04QA1/ZW6fV2ijmRHq/VUERMr8zzvVcgmu8xoI/UVLmG0w3MIzRX4Ewth5akzuna2lAXARZNL4G/4RW+J0TwO0GgXpqxvg0Fcxblu7PQU/txyR/iKE0YLzJRicrMk9K48BcTDi0FqNahLkO4hjX4IdkRGx09TxfR0Fh2p+6bVj2dLC3F3pU+612pq9k8q+MIiVJDv3SwdPbbamnjbzExggGbMIIBlwIBATB0MGwxEDAOBgNVBAYTB1Vua25vd24xEDAOBgNVBAgTB1Vua25vd24xEDAOBgNVBAcTB1Vua25vd24xEDAOBgNVBAoTB1Vua25vd24xEDAOBgNVBAsTB1Vua25vd24xEDAOBgNVBAMTB1Vua25vd24CBFBOLEIwCQYFKw4DAhoFADANBgkqhkiG9w0BAQEFAASCAQB4X7aa9k1LV33+Jk9ZPegFs5zEgoeeq5+Lbi2avAUm4SkDbqyCZgHkXMmQhxPlZp56IhBIZ1N10r8EF+MlQ2I2Kl4VIwhlFL40boMCyDLMdnhcJEB7vnlvdDzw+ehugnpk959SbCH9VxkWFQyhwPDd5tIvGUKDgp+J1dd9MPg0yoovEVqj9v5XnwAkxRhMYCpo5Q/WbSbujCO8aFI3H9YcQGzNnm6WEOkAM/bV42vwLvBGE9cdSKJXxHM9eF8O6irwOrmT9/ERzyGkVGh6RACTxzSEEgeY38NoVedQQVMtZxLpAm7KdvA1biI0w/VcJi5fIgcMfVXSKrPnUa6G09TL
                //The last parameter is a String Array containing one String which is a signature like the line above, but we have removed the signature check, so we pass a empty array.
                String[] emptyStrArr = new String[0];
                Array.set(webViewProviders, 0, constructorWebViewProviderInfo.newInstance("com.android.webview", "Android System WebView", false, false, emptyStrArr));
                //MIIDuzCCAqOgAwIBAgIJANi6DgBQG4ZTMA0GCSqGSIb3DQEBBQUAMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKDAtHb29nbGUgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDEQMA4GA1UEAwwHd2VidmlldzAeFw0xNDA4MDgyMzIwMjBaFw00MTEyMjQyMzIwMjBaMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKDAtHb29nbGUgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDEQMA4GA1UEAwwHd2VidmlldzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMbtaFX0r5aZJMAbPVMAgK1ZZ29dTn91VsGxXv2hqrQo7IpqEy2JmPvPnoMsSiuTAe+UcQy8oKDQ2aYVSAd1DGIy+nSRyFTt3LSIAdwSBkB1qT4a+OqkpsR6bSNXQXQ18lCQu9gREY3h3QlYBQAyzRxw4hRGlrXAzuSz1Ec4W+6x4nLG5DG61MAMR8ClF9XSqbmGB3kyZ70A0X9OPYYxiMWP1ExaYvpaVqjyZZcrPwr+vtW8oCuGBUtHpBUH3OoG+9s2YMcgLG7vCK9awKDqlPcJSpIAAj6uGs4gORmkqxZRMskLSTWbhP4p+3Ap8jYzTVB6Y1/DMVmYTWRMcPW0macCAwEAAaNQME4wHQYDVR0OBBYEFJ6bAR6/QVm4w9LRSGQiaR5Rhp3TMB8GA1UdIwQYMBaAFJ6bAR6/QVm4w9LRSGQiaR5Rhp3TMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEFBQADggEBAEQu8QiVxax7/diEiJrgKE1LwdXsIygJK/KnaKdnYEkAQpeu/QmrLiycm+OFbL1qHJIB7OuI/PQBUtcaNSiJSCVgtwtEbZWWIdsynqG/Nf4aGOndXegSQNRH54M05sRHLoeRycPrY7xQlEwGikNFR76+5UdwFBQI3Gn22g6puJnVukQm/wXQ+ajoiS4QclrNlixoDQsZ4STLH4+Wju2wIWKFFArIhVEIlbamq+p6BghuzH3aIz/Fy0YTQKi7SA+0fuNeCaqlSm5pYSt6p5CH89y1Fr+wFc5r3iLRnUwRcy08ESC7bZJnxV3d/YQ5valTxBbzku/dQbXVj/xg69H8l8M=
                Array.set(webViewProviders, 1, constructorWebViewProviderInfo.newInstance("com.google.android.webview", "Android System WebView", true, false, emptyStrArr));
                Array.set(webViewProviders, 2, constructorWebViewProviderInfo.newInstance("com.google.android.webview.beta", "Android System WebView Beta", false, false, emptyStrArr));
                Array.set(webViewProviders, 3, constructorWebViewProviderInfo.newInstance("com.google.android.webview.dev", "Android System WebView Dev", false, false, emptyStrArr));
                Array.set(webViewProviders, 4, constructorWebViewProviderInfo.newInstance("com.google.android.webview.canary", "Android System WebView Canary", false, false, emptyStrArr));
                //MIIEQzCCAyugAwIBAgIJAMLgh0ZkSjCNMA0GCSqGSIb3DQEBBAUAMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDAeFw0wODA4MjEyMzEzMzRaFw0zNjAxMDcyMzEzMzRaMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBAKtWLgDYO6IIrgqWbxJOKdoR8qtW0I9Y4sypEwPpt1TTcvZApxsdyxMJZ2JORland2qSGT2y5b+3JKkedxiLDmpHpDsz2WCbdxgxRczfey5YZnTJ4VZbH0xqWVW/8lGmPav5xVwnIiJS6HXk+BVKZF+JcWjAsb/GEuq/eFdpuzSqeYTcfi6idkyugwfYwXFU1+5fZKUaRKYCwkkFQVfcAs1fXA5V+++FGfvjJ/CxURaSxaBvGdGDhfXE28LWuT9ozCl5xw4Yq5OGazvV24mZVSoOO0yZ31j7kYvtwYK6NeADwbSxDdJEqO4k//0zOHKrUiGYXtqw/A0LFFtqoZKFjnkCAQOjgdkwgdYwHQYDVR0OBBYEFMd9jMIhF1Ylmn/Tgt9r45jk14alMIGmBgNVHSMEgZ4wgZuAFMd9jMIhF1Ylmn/Tgt9r45jk14aloXikdjB0MQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEUMBIGA1UEChMLR29vZ2xlIEluYy4xEDAOBgNVBAsTB0FuZHJvaWQxEDAOBgNVBAMTB0FuZHJvaWSCCQDC4IdGZEowjTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBAUAA4IBAQBt0lLO74UwLDYKqs6Tm8/yzKkEu116FmH4rkaymUIE0P9KaMftGlMexFlaYjzmB2OxZyl6euNXEsQH8gjwyxCUKRJNexBiGcCEyj6z+a1fuHHvkiaai+KL8W1EyNmgjmyy8AW7P+LLlkR+ho5zEHatRbM/YAnqGcFh5iZBqpknHf1SKMXFh4dd239FJ1jWYfbMDMy3NS5CTMQ2XFI1MvcyUTdZPErjQfTbQe3aDQsQcafEQPD+nqActifKZ0Np0IS9L9kR/wbNvyz6ENwPiTrjV2KRkEjH78ZMcUQXg0L3BYHJ3lc69Vs5Ddf9uUGGMYldX3WfMBEmh/9iFBDAaTCK
                Array.set(webViewProviders, 5, constructorWebViewProviderInfo.newInstance("com.android.chrome", "Chrome Stable", true, false, emptyStrArr));
                //MIIDwzCCAqugAwIBAgIJAOoj9MXoVhH6MA0GCSqGSIb3DQEBBQUAMHgxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKDAtHb29nbGUgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDEUMBIGA1UEAwwLY2hyb21lX2JldGEwHhcNMTYwMjI5MTUxNTIzWhcNNDMwNzE3MTUxNTIzWjB4MQswCQYDVQQGEwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzEUMBIGA1UECgwLR29vZ2xlIEluYy4xEDAOBgNVBAsMB0FuZHJvaWQxFDASBgNVBAMMC2Nocm9tZV9iZXRhMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAo/wW27nRxVqGbFOyXr8jtv2pc2Ke8XMr6Sfs+3JK2licVaAljGFpLtWH4wUdb50w/QQSPALNLSSyuK/94rtp5Jjs4RSJI+whuewV/R6El+mFXBO3Ek5/op4UrOsR91IM4emvS67Ji2u8gp5EmttVgJtllFZCbtZLPmKuTaOkOB+EdWIxrYiHVEEaAcQpEHa9UgWUZ0bMfPj8j3F0w+Ak2ttmTjoFGLaZjuBAYwfdctN1b0sdLT9Lif45kMCb8QwPp0F9/ozs0rrTc+I6vnTS8kfFQfk7GIE4Hgm+cYQEHkIA6gLJxUVWvPZGdulAZw7wPt/neOkazHNZPcV4pYuNLQIDAQABo1AwTjAdBgNVHQ4EFgQU5t7dhcZfOSixRsiJ1E46JhzPlwowHwYDVR0jBBgwFoAU5t7dhcZfOSixRsiJ1E46JhzPlwowDAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQUFAAOCAQEAZO2jB8P1d8ki3KZILvp27a2VM3DInlp8I8UgG3gh7nBQfTrnZr5M1PL8eFHqX7MEvAiGCMTcrPklEhjtcHK/c7BcdeCWq6oL56UK3JTl33RxJcjmjrz3e3VI6ehRSm1feNAkMD0Nr2RWr2LCYheAEmwTPtluLOJS+i7WhnXJzBtg5UpUFEbdFYenqUbDzya+cUVp0197k7hUTs8/Hxs0wf79o/TZXzTBq9eYQkiITonRN8+5QCBl1XmZKV0IHkzGFES1RP+fTiZpIjZT+W4tasHgs9QTTks4CCpyHBAy+uy7tApe1AxCzihgecCfUN1hWIltKwGZS6EE0bu0OXPzaQ==
                Array.set(webViewProviders, 6, constructorWebViewProviderInfo.newInstance("com.chrome.beta", "Chrome Beta", false, false, emptyStrArr));
                //MIIDwTCCAqmgAwIBAgIJAOSN+O0cdii5MA0GCSqGSIb3DQEBBQUAMHcxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKDAtHb29nbGUgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDETMBEGA1UEAwwKY2hyb21lX2RldjAeFw0xNjAyMjkxNzUwMDdaFw00MzA3MTcxNzUwMDdaMHcxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKDAtHb29nbGUgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDETMBEGA1UEAwwKY2hyb21lX2RldjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANOYPj6Y9rVt8xizSHDYjDEkDfFZAgSiZ9T6tevkQXsFyfaq3Gk3h2qssi29G6cTPJ2VXFKlVB71wSXv5p9/LEcDQPWQiO3Q2cLmgUXxyhJWXI3g96tPAhZQX2q6SC37ZQdiBR/raMO70DAkvCyBGtNplsvutzSE3oZ7LYfzB8vTbe7zCh3fDYSS/7xb3ZVvFqydHS40uVq1qqg1S80Pge7tW3pDGsPMZN7yA4yfmsvA1rbHm9N8t3Rc9hqzh6OxNAAgRB535YcsWL7iF+mpdFILXk3jLYT0nMvMnB83rsdgnRREjlGQYHl2mh8+6CqujsW/eICDq/LR6BYDyqHhk0ECAwEAAaNQME4wHQYDVR0OBBYEFKzsl07JglgpbeYDYGqsgqRDo+01MB8GA1UdIwQYMBaAFKzsl07JglgpbeYDYGqsgqRDo+01MAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEFBQADggEBACka6SFF6xAcj8L8O6R36++E09DTiGZEjvKT8eIycgcQQ+p1WUmPb6M2EJpN6zvvSE62ussmXdzf8rIyc0JXA8jbViZt62Y39epNENFxPTLN9QzXlT+w8AW73Ka3cnbOuL5EgoDl8fM79WVlARY3X+wB/jGNrkiGIdRm2IZIeAodWgC2mtXMiferyYBKz2/F2bhnU6DwgCbegS8trFjEWviijWdJ+lBdobn7LRc3orZCtHl8UyvRDi7cye3sK9y3BM39k0g20F21wTNHAonnvL6zbuNgpd+UEsVxDpOeWrEdBFN7Md0CI2wnu8eA8ljJD45v0WWMEoxsIi131g5piNM=
                Array.set(webViewProviders, 7, constructorWebViewProviderInfo.newInstance("com.chrome.dev", "Chrome Dev", false, false, emptyStrArr));
                //MIIDxzCCAq+gAwIBAgIJAML7APITsgV7MA0GCSqGSIb3DQEBBQUAMHoxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKDAtHb29nbGUgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDEWMBQGA1UEAwwNY2hyb21lX2NhbmFyeTAeFw0xNjAyMjkxOTA5MDdaFw00MzA3MTcxOTA5MDdaMHoxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKDAtHb29nbGUgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDEWMBQGA1UEAwwNY2hyb21lX2NhbmFyeTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANXfeAoZlr0ya1HBzIfAz/nLLjpPJeAPvuX5dueaxmiQgv2hNG22acriFuiiJI6TU0t8AIVJD5Ifbc4OOuA0zeFhdzWWGnmTRH6x27WI7bzOKnAqOvv21ZBmE9i8Vo++K13xWdTs3qVn1bn9oUONxFu0wKDzXYZhoj1Jom0RZGjXm16xuPlEuOzMcjiNBDoYuxPAXkMcK/G1gP4P4nAV8Rd/GGIjKRS/SUtcShhoAMOQhs4WIEkUrvEVRwhBDIbpM87oFbCVdBH38r0XS6F6CdhPJsKFhoEfq4c01HZqNmDpCPA8AAcCuSWqmXoTIqs7OqkWgduE2bInbWU7WMaTl+kCAwEAAaNQME4wHQYDVR0OBBYEFB/AsC4iPAqaLoNytNSx29qByI7+MB8GA1UdIwQYMBaAFB/AsC4iPAqaLoNytNSx29qByI7+MAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEFBQADggEBAMb2Td3ro/+MGVnCPAbwBSOZMVLUKGqt6zr8CShW9mtFHnmy29EaWSYYAj1M4+6Vpkq85NsgBEck7rnUjV8A3Q0NKdTys1KRKJqVvQRBN6SwqQenSf/abxQCa8Z+69rh+3BkIU1HLtu5lrMDZwon5H91L5mpORn6vItd20uW132lwSDeUEW2CHslTrodoFuTUcSUlRiq/URfUH3baO1QHXkxpQwrBPKL5deJfcZnxh5MAtAGSQL7gHvayEFlDppETXdDO7vgGTH2dEK2TjKWALbGiKkxSqjRyTNt4/FOj10TqNRdUamj+ydVJgzGQ8bki4Vc6NnKm/r4asusxapkVR4=
                Array.set(webViewProviders, 8, constructorWebViewProviderInfo.newInstance("com.chrome.canary", "Chrome Canary", false, false, emptyStrArr));
                //No Signature
                Array.set(webViewProviders, 9, constructorWebViewProviderInfo.newInstance("com.google.android.apps.chrome", "Chrome Debug", false, false, emptyStrArr));
                //https://www.bromite.org/system_web_view
                //MIIFRgYJKoZIhvcNAQcCoIIFNzCCBTMCAQExDzANBglghkgBZQMEAgEFADALBgkqhkiG9w0BBwGgggNxMIIDbTCCAlWgAwIBAgIEHcsmjjANBgkqhkiG9w0BAQsFADBmMQswCQYDVQQGEwJERTEQMA4GA1UECBMHVW5rbm93bjEPMA0GA1UEBxMGQmVybGluMRAwDgYDVQQKEwdCcm9taXRlMRAwDgYDVQQLEwdCcm9taXRlMRAwDgYDVQQDEwdjc2FnYW41MCAXDTE4MDExOTA3MjE1N1oYDzIwNjgwMTA3MDcyMTU3WjBmMQswCQYDVQQGEwJERTEQMA4GA1UECBMHVW5rbm93bjEPMA0GA1UEBxMGQmVybGluMRAwDgYDVQQKEwdCcm9taXRlMRAwDgYDVQQLEwdCcm9taXRlMRAwDgYDVQQDEwdjc2FnYW41MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtakjGj0eTavbBB2vWXj8KBixWn4zgXAKc+yGFu3SLEGF1VB5aJWwcMHxVI55yH/8M2eNnJP0BkSidfKgPVcm1sk/GrNEs9uk5sWod9byO5M5QWQmGP2REeTd6J0BVVVaMp2MZnqeR3Su3pwFzrSwTqIGyf8dkPSEz7ifj792+EeRNrov4oRQK7lIfqInzwc4d34wU069Lrw6m7J7HM0KbRYISsWMiYj025Qg+dTrtdWt7jbdcj7htW0eYyJoLd90+s43RWnOpENmWpcWv1EVPxUD4mCdV9idYwoHRIESpSu9IWvqDZp1VoRc43nLgsNfNBwmYdTkIaPiz1m7TBcr7QIDAQABoyEwHzAdBgNVHQ4EFgQUuWoGd7W7wMyQ1pOdjiMv10YHTR0wDQYJKoZIhvcNAQELBQADggEBAA7iw6eKz+T8HIpKDoDcX1Ywjn9JUzuCFu20LnsLzreO/Pog1xErYjdLAS7LTZokfbAnitBskO9QhV9BYkDiM0Qr5v2/HsJTtxa1mz9ywCcI36jblMyuXFj8tuwQI9/t9i+Fc3+bOFBV3t7djPo9qX1dIK0lZ6s8HcIhaCNdqm65fH+nWhC/H9djqC6qOtrkTiACKEcHQ4a/5dfROU0q0M4bS4YuiaAQWgjiGbik4LrZ8wZX1aqJCLt0Hs7MzXyyf0cRSO11FIOViHwzh6WTZGufq2J3YBFXPond8kLxkKL3LNezbi5yTcecxsbKQ6OS46CnIKcy/M8asSreLpoCDvwxggGZMIIBlQIBATBuMGYxCzAJBgNVBAYTAkRFMRAwDgYDVQQIEwdVbmtub3duMQ8wDQYDVQQHEwZCZXJsaW4xEDAOBgNVBAoTB0Jyb21pdGUxEDAOBgNVBAsTB0Jyb21pdGUxEDAOBgNVBAMTB2NzYWdhbjUCBB3LJo4wDQYJYIZIAWUDBAIBBQAwDQYJKoZIhvcNAQEBBQAEggEAP0yKQoHoWfk+MYorwyThygO9YU3uFWRUN+dDZ64TcVIOY0EWaKZbb3ysbK+fvx9yd6QmLmWz1HRrkrSa0RAmXQp9FuIhB7A+IwWf9M/0FWrjaimF131ZD4+V1X11KZJkrSQ2vkwZkOhDts4FN2nd9eVjjRaepdPjAzmRxBvo7x3oCpbtLBZfddjNnEaXgBXnEV4HpoEGWr+tSjs3U2ftkENNkWXQPeUsY0fV8aqngef8Emn73UxRMT9w/IGZnfYymu34nnOa75ha0/ZIgTngLMKq5C8hE16fjahCXJUGmqUGexWEWKTr9TgSBfBOZlpPNaGtrSZfK6v2zNrOroYTwQ==
                Array.set(webViewProviders, 10, constructorWebViewProviderInfo.newInstance("org.bromite.webview", "Bromite System WebView", false, false, emptyStrArr));
                //https://gitlab.com/divested-mobile/mulch
                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
                Array.set(webViewProviders, 11, constructorWebViewProviderInfo.newInstance("us.spotco.mulch_wv", "Mulch System WebView", false, false, emptyStrArr));
                //https://gitlab.com/grapheneos/platform_external_vanadium/-/tree/13/prebuilt/arm64
                //MIIEyTCCArGgAwIBAgIEK42mijANBgkqhkiG9w0BAQ0FADAVMRMwEQYDVQQDEwpHcmFwaGVuZU9TMB4XDTE5MDcxODIxMDczMFoXDTQ2MTIwMzIxMDczMFowFTETMBEGA1UEAxMKR3JhcGhlbmVPUzCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAKGs5ZOM6G5jLcObZMYDL2CYLwn5niwSItbV3ZfMi2pypv1PnWwbUg6lPO5CrBGNOPGr0nXTpStL0TnovmMIOt+pMlBK3sqjAUZddcMAf0O6WdKt4d57hHj/iQJiR7w6QBnh06nylrhZw2ILxdazkZbtUchtvqGkBWPIjSgL52GRBRtEckUCaMymgBr060CNlE7YtgdwmXv3Wc+GqbA4sF/ItK4JOOZEmmHLyQQc6bLBOc1VSGCzLVzgmOBiuyQOzq3AXSDTyeVJT1QSnHp1r0fOkzcDpv0+FD3raFXFLQFPpSZCCxuMBpIsSdyHjzfxdRBjjuSdhxX9zqRT/7Si2lWM31Ro/5aFVzcMbx6/v7OZsecM8ZnU65SPcoGkaRhx1+Ng4Zr8VoLU62vvaT1u6ZeHD8v10vvqK7xlPxyvWjWg7h1HTGBiLTGP3NRILlOvj6zd/xQqZrfJdRSiOsmvGwe0zYN4lp6qqbR+uqk+vwU3KsmhBaw6wKlw+qsX4o3r036JDPgsVtXW8lcZfF3P5kKWkzcE2drfDFvqigVDX6oM4RgNydjPdoCYph1ohu6+l4y5YRVFoSNLWqs6alvzXBBGWYPoTt5PcPC2BZqS3+8xBJFvBTRsRm4ZfDUmY8naOAYe+EhG5xN8+bIe5gSGNpB6d1+iS2j88o+EVbX73t6BAgMBAAGjITAfMB0GA1UdDgQWBBRgFIhZyUCYfYFLo0VmQsLHllG/QTANBgkqhkiG9w0BAQ0FAAOCAgEAS9tO9Xm4ur27wL6WCsZXk5Xwpyup0x/efaFhHa9LFVhoHKYR2BfG000+2aqhKAHsHUmIwaO0GieP7rvAS6mGGfmhWGOTFM2nz4XKy4bliB9E7MbAdN56tThPcfnCKZQGLsfYwbM7oWFqyuVc50pzpGoprBXyJx9wmsaBhOdV2Bx7f9dEKgk/B2VDQ2nzPmzgayjsvNpRcjgRMQtN9mmaIhErg6FYzzoPUnFGpgkvC754K2j9SkKSDM8dilRTx3FuBZbtJy7nOOekxZneVkJEjocvpFhZaJ2TPwYS/WhdMOJNdABQxGSel5VXpCVg5NM06R3wPdsjaviOrrTwwH+oqxZE14iyH38Uv/lZ5i7S+l93cpBG5/RJOoOlGkaMkZoMMnN8OcIGSBQWfKf1VnQQJSuqt/7jgromGJwSA7OUvU0+v+gVPxM7vvcojLOErKEQ1aiqUV39QTA/4ZgfgWC5kXdHdHS2+8bu5hgsMkdVAYWW6FJPfwSiPIzoiRZBlv8VElKk7i2QYfLUkn66iNLApQoCrt2EUshYi94dW5r50DGOuIftvktTsheRr93J1qfafqorFMhJOM2buKDlVr1eUJ2qxay0YIwv8yo9QouG9K4AaMhodWAaHrDRMHPwDSdeHe5LHoeGzbyj5WzBKS3l/cuu5E/LN9LbnyN2dsvDbTY=
                Array.set(webViewProviders, 12, constructorWebViewProviderInfo.newInstance("app.vanadium.webview", "Vanadium System WebView", false, false, emptyStrArr));

                //You can add your webviews here
            } catch (Throwable t) {
                log(t);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                classWebViewUpdater = findClass("com.android.server.webkit.WebViewUpdateServiceImpl", lpparam.classLoader);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                classWebViewUpdater = findClass("com.android.server.webkit.WebViewUpdater", lpparam.classLoader);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                classWebViewUpdater = findClass("com.android.server.webkit.WebViewUpdateServiceImpl$WebViewUpdater", lpparam.classLoader);
            }

            return this;
        }
    }
}
