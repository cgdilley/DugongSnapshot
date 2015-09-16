package com.sprelf.dugongsnapshot;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.kii.cloud.storage.Kii;
import com.kii.cloud.storage.KiiObject;
import com.kii.cloud.storage.callback.KiiQueryCallBack;
import com.kii.cloud.storage.query.KiiQuery;
import com.kii.cloud.storage.query.KiiQueryResult;

import java.util.List;


public class ImageTestActivity extends Activity
{

    private List<KiiObject> imageList;
    private int imageIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_test);

        Kii.initialize(DugongSnapshot.KII_APPID, DugongSnapshot.KII_APPKEY, DugongSnapshot.KII_SITE);

        KiiQuery query = new KiiQuery();


        Kii.bucket(DugongSnapshot.DATA_BUCKET).query(new KiiQueryCallBack<KiiObject>()
        {
            @Override
            public void onQueryCompleted(int token, KiiQueryResult<KiiObject> result, Exception exception)
            {
                if (exception != null)
                {
                    exception.printStackTrace();
                }
                else
                {
                    imageList = result.getResult();

                    if (!imageList.isEmpty())
                    {
                        ImageView imageView = (ImageView) findViewById(R.id.Test_Image);

                        byte[] imageData = imageList.get(0).getByteArray("image");
                        Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

                        imageView.setImageBitmap(bitmap);
                    }
                }
            }
        }, query);


    }

    public void imageCycle(View view)
    {
        if (!imageList.isEmpty())
        {
            imageIndex++;
            if (imageIndex >= imageList.size())
            {
                imageIndex = 0;
            }

            ImageView imageView = (ImageView) findViewById(R.id.Test_Image);

            byte[] imageData = imageList.get(imageIndex).getByteArray("image");
            if (imageData != null && imageData.length > 0)
            {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

                imageView.setImageBitmap(bitmap);
            }
        }


    }


}
