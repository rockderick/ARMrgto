package rockderick.ar.video.marugoto;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        myToolbar.setTitle(Html.fromHtml("<font color='#FFFFFF'>Marugoto AR</font>"));
        setSupportActionBar(myToolbar);

    }

    public void onClickStart(View v)
    {
        Intent intent = new Intent(this, CameraMainActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(rockderick.ar.video.marugoto.R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        /*if (id == rockderick.ar.video.marugoto.R.id.action_settings) {
            return true;
        }*/

        if (id == R.id.action_howto) {
            showHowTo();
            return true;
        }
        else if (id == R.id.about_mrgto) {
            showAbout();
            return true;
        }




        return super.onOptionsItemSelected(item);
    }

    public void showHowTo(){
        Intent intent = new Intent(this, HwToActivity.class);
        startActivity(intent);
    }

    public void showAbout(){
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }

}
