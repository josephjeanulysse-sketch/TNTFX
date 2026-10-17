package fr.tntfx.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS = "tntfx";
    private static final String PLAYLIST = "playlist";
    private LinearLayout root, listPanel;
    private PlayerView playerView;
    private ExoPlayer player;
    private boolean playing = false;
    private final List<Channel> channels = new ArrayList<>();
    private static class Channel {
        String name, url;
        Channel(String n, String u) { name=n; url=u; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        loadPlaylist();
        showHome();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(18); b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(42,43,49)));
        b.setFocusable(true); b.setPadding(dp(16), dp(8), dp(16), dp(8)); return b;
    }
    private void base() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(16,17,20)); root.setPadding(dp(28),dp(18),dp(28),dp(18)); setContentView(root);
    }
    private void showHome() {
        playing = false; base();
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setOrientation(LinearLayout.HORIZONTAL);
        TextView logo = text("TNT ", 32, Color.WHITE); logo.setTypeface(null, 1); header.addView(logo);
        TextView fx = text("FX", 32, Color.rgb(241,43,53)); fx.setTypeface(null,1); header.addView(fx);
        TextView sub = text("     Télévision en direct", 17, Color.LTGRAY); header.addView(sub); root.addView(header);
        TextView info = text("Sélectionne une chaîne. Pour ajouter les flux autorisés, importe une playlist M3U.", 16, Color.LTGRAY); info.setPadding(0,dp(8),0,dp(14)); root.addView(info);
        Button add = button("Ajouter / importer une playlist M3U"); add.setOnClickListener(v -> playlistDialog()); root.addView(add);
        TextView title = text("MES CHAÎNES", 15, Color.rgb(241,43,53)); title.setPadding(0,dp(18),0,dp(8)); root.addView(title);
        listPanel = new LinearLayout(this); listPanel.setOrientation(LinearLayout.VERTICAL); root.addView(listPanel);
        if (channels.isEmpty()) {
            TextView empty = text("Aucun flux configuré pour le moment.\nAjoute l’URL d’une playlist M3U accessible légalement.", 18, Color.WHITE);
            empty.setGravity(Gravity.CENTER); empty.setPadding(dp(12),dp(22),dp(12),dp(22)); listPanel.addView(empty);
        } else {
            for (int i=0;i<channels.size();i++) {
                final Channel c = channels.get(i); Button b = button("▶   " + c.name); b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54)); lp.bottomMargin=dp(6); listPanel.addView(b,lp);
                b.setOnClickListener(v -> play(c));
                if (i==0) b.requestFocus();
            }
        }
    }

    private void playlistDialog() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),0);
        EditText url = new EditText(this); url.setSingleLine(true); url.setTextColor(Color.WHITE); url.setHintTextColor(Color.LTGRAY); url.setHint("https://exemple.fr/playlist.m3u"); url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(getPreferences(0).getString(PLAYLIST,"")); box.addView(url);
        new AlertDialog.Builder(this).setTitle("Playlist M3U").setMessage("Colle l’adresse HTTPS d’une playlist que tu es autorisé à utiliser.")
            .setView(box).setNegativeButton("Annuler",(d,w)->{}).setNeutralButton("Effacer",(d,w)->{getPreferences(0).edit().remove(PLAYLIST).apply();channels.clear();showHome();})
            .setPositiveButton("Charger",(d,w)->{String u=url.getText().toString().trim(); if(!u.isEmpty()){getPreferences(0).edit().putString(PLAYLIST,u).apply(); parsePlaylist(u);} }).show();
    }

    private void loadPlaylist() {
        String u=getPreferences(0).getString(PLAYLIST,""); if(!u.isEmpty()) parsePlaylist(u);
    }
    private void parsePlaylist(String url) {
        channels.clear();
        Toast.makeText(this,"Chargement de la playlist…",Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                java.net.URL u=new java.net.URL(url); java.net.HttpURLConnection c=(java.net.HttpURLConnection)u.openConnection(); c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestProperty("User-Agent","TNT-FX/0.1 AndroidTV");
                java.io.BufferedReader r=new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(),"UTF-8"));
                String line, name="Chaîne"; List<Channel> found=new ArrayList<>();
                while((line=r.readLine())!=null){line=line.trim(); if(line.startsWith("#EXTINF")){int comma=line.lastIndexOf(','); if(comma>=0) name=line.substring(comma+1).trim();} else if(!line.isEmpty()&&!line.startsWith("#")&&(line.startsWith("https://")||line.startsWith("http://"))){found.add(new Channel(name,line));name="Chaîne";}}
                r.close(); c.disconnect(); runOnUiThread(()->{channels.addAll(found);showHome(); if(found.isEmpty())Toast.makeText(this,"Aucune chaîne trouvée dans cette playlist.",Toast.LENGTH_LONG).show();});
            } catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Impossible de charger la playlist. Vérifie l’URL et la connexion.",Toast.LENGTH_LONG).show());}
        }).start();
    }

    private void play(Channel c) {
        base(); playerView = new PlayerView(this); playerView.setUseController(true); root.addView(playerView,new LinearLayout.LayoutParams(-1,-1));
        player = new ExoPlayer.Builder(this).build(); playerView.setPlayer(player);
        player.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException error){Toast.makeText(MainActivity.this,"Flux indisponible ou format non pris en charge.",Toast.LENGTH_LONG).show();}});
        player.setMediaItem(MediaItem.fromUri(Uri.parse(c.url))); player.prepare(); player.play(); playing=true;
        playerView.setFocusable(true); playerView.requestFocus();
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(playing && (keyCode==KeyEvent.KEYCODE_BACK)){ releasePlayer(); showHome(); return true; }
        return super.onKeyDown(keyCode,event);
    }
    private void releasePlayer(){if(player!=null){player.release();player=null;}playing=false;}
    @Override protected void onStop(){super.onStop();releasePlayer();}
}
