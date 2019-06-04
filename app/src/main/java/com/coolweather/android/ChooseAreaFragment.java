package com.coolweather.android;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.coolweather.android.db.City;
import com.coolweather.android.db.County;
import com.coolweather.android.db.Province;
import com.coolweather.android.util.HttpUtil;
import com.coolweather.android.util.Utility;

import org.litepal.LitePal;
import org.litepal.crud.LitePalSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {

    public static final int LEVEL_PROVINCE = 0;

    public static final int LEVEL_CITY = 1;

    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog progressDialog;

    private TextView titleText;         //标题文本框

    private Button backButton;          //按钮

    private ListView listView;          //滚动内容

    private ArrayAdapter<String> adapter;

    private List<String> dataList = new ArrayList<>();

    /**
     * 省列表数据
     */
    private List<Province> provincesList;

    /**
     * 市列表的数据
     */
    private List<City> cityList;

    /**
     * 县列表的数据
     */
    private List<County> countyList;

    /**
     * 选中那个省份的数据
     */
    private Province selectedProvince;

    /**
     * 选中那个市的数据
     */
    private City selectedCity;

    /**
     * 当前选中的级别 判断
     */
    private int currentLevel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area,container,false);
        titleText = (TextView)view.findViewById(R.id.title_text);
        backButton = (Button)view.findViewById(R.id.back_button);
        listView = (ListView)view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(),android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE){
                    selectedProvince = provincesList.get(position);    //选中在那个省
                    queryCities();
                }else if (currentLevel == LEVEL_CITY){
                    selectedCity = cityList.get(position);   //选中在那个市
                    queryCounties();
                }else if (currentLevel == LEVEL_COUNTY){          //选中级别到县级的时候
                    String weatherId = countyList.get(position).getWeatherId();    //将天气id取出来
                    Intent intent = new Intent(getActivity(),WeatherActivity.class);   //跳转到天气界面
                    intent.putExtra("weather_id",weatherId);                 //将县的天气id传过去
                    startActivity(intent);
                    getActivity().finish();        //关闭活动
                }
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentLevel == LEVEL_COUNTY){
                    queryCities();      //返回按钮   从县返回到市的级别
                }else if (currentLevel == LEVEL_CITY){
                    queryProvinces();    //返回按钮  从市返回到省的级别
                }
            }
        });
        queryProvinces();     //第一次运行会调用方法     全部的内容都会从服务器读取然后储存到数据库在操作UI
    }

    /**
     * 查询全国所以省，优先从数据库查询，如果没有查询到再去服务器上查询
     */
    private void queryProvinces(){
        titleText.setText("中国");        //设置标题文字
        backButton.setVisibility(View.GONE);    //隐藏按钮
        provincesList = LitePal.findAll(Province.class);   //查询获取数据库的内容到省列表
        if (provincesList.size() > 0){             //判断数据库是否有内容
            dataList.clear();                      //清空集合
            for (Province province : provincesList){                 //循环所有省的数据
                dataList.add(province.getProvinceName());          //把所有省的名字添加到集合中
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;           //设置选中的级别
        }else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address,"province");
        }
    }

    /**
     * 查询省中所有市，优先从数据库查询，如果没有查询到再去服务器上查询
     */
    private void queryCities(){
        titleText.setText(selectedProvince.getProvinceName());    //选中那个省的名字
        backButton.setVisibility(View.VISIBLE);            //显示按钮
        cityList = LitePal.where("provinceId = ?",String.valueOf(selectedProvince.getId())).find(City.class);   //查询那个省中的市的所有数据
        if (cityList.size() > 0){
            dataList.clear();
            for(City city : cityList){
                dataList.add(city.getCityName());    //把所有市的数据传到集合
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        }else {
            int provinceCode = selectedProvince.getProvinceCode();          //选中那个省的代号，要获取里面市的数据
            String address = "http://guolin.tech/api/china/" + provinceCode;
            queryFromServer(address,"city");
        }
    }

    /**
     * 查询市中所有县，优先从数据库查询，如果没有查询到再去服务器上查询
     */
    private void queryCounties(){

        titleText.setText(selectedCity.getCityName());       //设置选中那个市名字
        backButton.setVisibility(View.VISIBLE);             //显示按钮
        countyList = LitePal.where("cityId = ?",String.valueOf(selectedCity.getId())).find(County.class);  //查询那个市中所有县的数据
        if (countyList.size() > 0){
            dataList.clear();
            for (County county : countyList){
                dataList.add(county.getCountyName());  //把所有县数据的传到集合
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        }else {
            int provinceCode = selectedProvince.getProvinceCode();    //选中那个省的代号
            int cityCode = selectedCity.getCityCode();                 //选中那个市的代号id
            String address = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;     //得到县的URL传过去
            queryFromServer(address,"county");
        }
    }

    /**
     * 根据传入的地址和类型从服务器上查询省市县数据
     */
    private void queryFromServer(String address,final String type){
        showProgProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(),"加载失败",Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                String responseText = response.body().string();          //得到服务器内容
                boolean result = false;
                if ("province".equals(type)){
                    result = Utility.handleProvinceResponse(responseText);    //解析所有省的数据储存到数据库中
                }else if ("city".equals(type)){
                    result = Utility.handleCityResponse(responseText,selectedProvince.getId());  //解析那个省id的所有市的
                }else if ("county".equals(type)){
                    result = Utility.handleCountyResponse(responseText,selectedCity.getId());//解析那个市id的所有县的
                }
                if (result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                                       //关闭对话框
                            if ("province".equals(type)){
                                queryProvinces();              //切换主线程从新调用这方法操作UI
                            }else if ("city".equals(type)){
                                queryCities();
                            }else if ("county".equals(type)){
                                queryCounties();

                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * 显示对话框
     */
    private void showProgProgressDialog(){
        if (progressDialog == null){
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载。。。");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }


    /**
     * 关闭对话框
     */
    private void closeProgressDialog(){
        if (progressDialog != null){
            progressDialog.dismiss();
        }
    }
}
