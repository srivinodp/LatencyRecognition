package com.example.latencyrecognition;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    int frame_index;
    int [] latency = new int[1001];
    boolean []printed = new boolean[1001];
    long mDuration,mTotalFrames;
    int mFrameRate;
    ImageView imageView, cropView;
    TextView textView;
    Button button;
    String text_identified;
    MediaMetadataRetriever mRetriever;
    Bitmap bitmap,cropped_bitmap;
    TextRecognizer recognizer = TextRecognition.getClient();
    static final int DELAY = 5, FILE_CODE = 28;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Arrays.fill(latency,0,1001,-1);
        Arrays.fill(printed,0,1001,false);
        frame_index = 0;
        imageView = findViewById(R.id.showFrame);
        cropView = findViewById(R.id.cropped);
        button = findViewById(R.id.videoselect);
        textView = findViewById(R.id.textview);
        mRetriever = new MediaMetadataRetriever();
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("video/mp4");
                startActivityForResult(Intent.createChooser(intent,"PICK THE VIDEO FILE"),FILE_CODE);
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode==RESULT_OK&&requestCode==FILE_CODE){
            mRetriever.setDataSource(this,data.getData());
            Log.d(TAG, "onActivityResult: Video path set successfully");
            mDuration = Integer.parseInt(mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            mTotalFrames = Long.parseLong(mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT));
            mFrameRate = (int)Math.ceil(mTotalFrames*1000.0/mDuration);
            Log.d(TAG, "onActivityResult: Time(sec)-"+mDuration/1000+" FPS-"+mFrameRate+" TotalFrames-"+mTotalFrames);
            bitmap = mRetriever.getFrameAtIndex(0);
            Log.d(TAG, "onActivityResult: width X height-"+bitmap.getWidth()+" X "+bitmap.getHeight());
            startParsing();
        }
    }
    void startParsing(){
        final Handler mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.P)
            @Override
            public void run() {
                if(frame_index<mTotalFrames){
                    if(latency[frame_index/mFrameRate+1]==-1) recognize(frame_index);
                    frame_index+=mFrameRate;
                }
                mHandler.postDelayed(this,DELAY);
            }
        }, DELAY);
    }
    @RequiresApi(api = Build.VERSION_CODES.P)
    void recognize(int frame_index){
        if(latency[frame_index/mFrameRate]!=-1) return;
        bitmap = mRetriever.getFrameAtIndex(frame_index);
        if(latency[frame_index/mFrameRate]!=-1) return;
        cropped_bitmap = Bitmap.createBitmap(bitmap,2300,300,100,50);
        InputImage image = InputImage.fromBitmap(cropped_bitmap,0);
        recognizer.process(image).addOnSuccessListener(new OnSuccessListener<Text>() {
            @Override
            public void onSuccess(Text text) {
                if(!text.getTextBlocks().isEmpty()){
                    text_identified = text.getTextBlocks().get(0).getText();
                    if(text_identified.contains("ms")||text_identified.contains("mS")||text_identified.contains("m5")){
                        if(text_identified.length()>2){
                            String num = text_identified.substring(0,text_identified.length()-2);
                            if(num!=null&&num.matches("[-+]?\\d\\.?\\d+")){
                                latency[frame_index/mFrameRate+1] = Integer.parseInt(num);
                                imageView.setImageBitmap(bitmap);
                                cropView.setImageBitmap(cropped_bitmap);
                                textView.setText((frame_index/mFrameRate+1) +", "+num);
                                if(!printed[frame_index/mFrameRate+1]){
                                    printed[frame_index/mFrameRate+1] = true;
                                    Log.d(TAG, "onSuccess: Text identified-"+(frame_index/mFrameRate+1)+", "+num);
                                }
                            }
                        }
                    }
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "onFailure: Recognition Failed!!");
            }
        });
    }
}