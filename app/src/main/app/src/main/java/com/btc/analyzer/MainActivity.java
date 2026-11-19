
package com.btc.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

public class MainActivity extends Activity {

    TextView price, signal, detail, status;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(32, 48, 32, 24);
        layout.setBackgroundColor(Color.rgb(14, 19, 30));

        TextView title = addText(layout,
            "BTC FUTURES ANALYZER", 25, Color.WHITE);

        price = addText(layout,
            "BTC/USDT --", 26, Color.WHITE);

        signal = addText(layout,
            "분석 대기", 24, Color.YELLOW);

        detail = addText(layout,
            "EMA 9 / EMA 21", 17, Color.LTGRAY);

        status = addText(layout,
            "데이터 연결 전", 15, Color.LTGRAY);

        Button refresh = new Button(this);
        refresh.setText("실시간 분석 새로고침");
        layout.addView(refresh);

        refresh.setOnClickListener(v -> loadData());

        setContentView(layout);
        loadData();
    }

    TextView addText(LinearLayout box,
                     String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, 12, 0, 18);
        box.addView(view);
        return view;
    }

    double ema(double[] values, int period) {
        double value = values[0];
        double alpha = 2.0 / (period + 1);

        for (int i = 1; i < values.length; i++) {
            value = alpha * values[i]
                    + (1 - alpha) * value;
        }
        return value;
    }

    void loadData() {
        status.setText("시장 데이터 조회 중...");

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(
                    "https://fapi.binance.com/fapi/v1/klines"
                    + "?symbol=BTCUSDT&interval=5m&limit=100"
                );

                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() != 200) {
                    throw new Exception(
                        "HTTP " + conn.getResponseCode()
                    );
                }

                BufferedReader reader =
                    new BufferedReader(
                        new InputStreamReader(
                            conn.getInputStream()
                        )
                    );

                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();

                JSONArray candles =
                    new JSONArray(json.toString());

                // 마지막 미완성 캔들은 제외
                int n = candles.length() - 1;
                if (n < 30) {
                    throw new Exception("캔들 데이터 부족");
                }

                double[] closes = new double[n];

                for (int i = 0; i < n; i++) {
                    closes[i] = Double.parseDouble(
                        candles.getJSONArray(i).getString(4)
                    );
                }

                double last = closes[n - 1];
                double fast = ema(closes, 9);
                double slow = ema(closes, 21);

                String direction;
                int color;

                if (fast > slow && last > fast) {
                    direction = "LONG 우위";
                    color = Color.GREEN;
                } else if (fast < slow && last < fast) {
                    direction = "SHORT 우위";
                    color = Color.RED;
                } else {
                    direction = "중립 / 관망";
                    color = Color.YELLOW;
                }

                String p = String.format(Locale.US,
                    "BTC/USDT $%,.2f", last);

                String d = String.format(Locale.US,
                    "5분봉 기준\nEMA9: %,.2f\nEMA21: %,.2f",
                    fast, slow);

                runOnUiThread(() -> {
                    price.setText(p);
                    signal.setText(direction);
                    signal.setTextColor(color);
                    detail.setText(d);
                    status.setText(
                        "최근 완성된 5분봉 기준 분석 완료"
                    );
                });

            } catch (Exception e) {
                String error = e.getMessage();
                runOnUiThread(() -> {
                    signal.setText("분석 불가");
                    signal.setTextColor(Color.YELLOW);
                    status.setText(
                        "데이터 연결 실패: " + error
                    );
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
