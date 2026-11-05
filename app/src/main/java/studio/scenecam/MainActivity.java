package studio.scenecam;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

@SuppressWarnings("deprecation")
public final class MainActivity extends Activity {
    private LinearLayout audioList;private TextView audioMeter;
    private final Handler handler=new Handler();private LinearLayout list;private TextView status;private Button start;private Preview preview;private Layer selected;
    private int ink=Color.rgb(228,232,240),muted=Color.rgb(141,154,177),accent=Color.rgb(82,227,176);
    private final Runnable refresh=new Runnable(){public void run(){if(preview!=null)preview.invalidate();if(status!=null){
        String text=Studio.status;for(Layer l:Studio.layers)if(!l.error.isEmpty())text+="\n"+l.name+": "+l.error;
        if(audioMeter!=null){StringBuilder meter=new StringBuilder("Звук · ");meter.append(Math.round(AudioBus.level*100)).append("%\n");for(AudioLayer layer:Studio.audio)meter.append(layer.name).append(layer.enabled?" · "+Math.round(layer.level*100)+"%":" · выключен").append(layer.error.isEmpty()?"":" · "+layer.error).append("\n");audioMeter.setText(meter.toString());}
        status.setText(text);start.setText(Studio.running?"■  Остановить камеру":"●  Запустить камеру");}handler.postDelayed(this,Math.max(16,1000/Studio.previewFps));}};
    public void onCreate(Bundle b){super.onCreate(b);Studio.load(this);getWindow().setStatusBarColor(Color.rgb(13,18,27));getWindow().setNavigationBarColor(Color.rgb(13,18,27));build();}
    protected void onResume(){super.onResume();handler.post(refresh);}
    protected void onPause(){handler.removeCallbacks(refresh);Studio.save(this);super.onPause();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextColor(ink);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(28,38,52)));b.setAllCaps(false);b.setOnClickListener(v->r.run());return b;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(13,18,27));LinearLayout root=column();root.setPadding(dp(20),dp(18),dp(20),dp(24));root.setBackgroundColor(Color.rgb(13,18,27));scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(20)+insets.getSystemWindowInsetLeft(),dp(18)+insets.getSystemWindowInsetTop(),dp(20)+insets.getSystemWindowInsetRight(),dp(24)+insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        root.addView(text("SCENECAM   /   STUDIO",13,accent));root.addView(text("Виртуальная камера",28,ink));
        TextView caption=text("Соберите кадр. Передайте его в приложение.",13,muted);caption.setPadding(0,dp(8),0,dp(20));root.addView(caption);
        root.addView(text("ПРЕДПРОСМОТР · перемещение и масштаб жестами",12,muted));preview=new Preview();LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(getPreferences(0).getInt("previewHeight",210)));pp.topMargin=dp(10);root.addView(preview,pp);
        status=text(Studio.status,12,muted);status.setPadding(0,dp(10),0,dp(10));root.addView(status);
        start=button("●  Запустить камеру",()->{if(Studio.running){stopService(new Intent(this,StudioService.class));return;}startStudio();});start.setTextColor(accent);root.addView(start);
        root.addView(button("＋ Источник",this::addSource));
        root.addView(button("Размер сцены и масштабирование",this::sceneSettings));
        root.addView(button("Диагностика подключения",()->{
            String details="SceneCam 0.9\n"+Studio.status+"\n"+Diagnostics.text();
            new AlertDialog.Builder(this).setTitle("Модуль LSPosed").setMessage(details).setPositiveButton("Закрыть",null)
                .setNeutralButton("Копировать",(d,n)->{android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);clipboard.setPrimaryClip(ClipData.newPlainText("SceneCam",details));}).show();
        }));
        TextView heading=text("ИСТОЧНИКИ   ·   верхний слой в конце списка",12,muted);heading.setPadding(0,dp(22),0,dp(10));root.addView(heading);list=column();root.addView(list);renderList();
        TextView audioHeading=text("АУДИОМИКШЕР",14,accent);audioHeading.setPadding(0,dp(20),0,dp(8));root.addView(audioHeading);
        root.addView(button("＋ Источник звука",this::addAudio));audioMeter=text("Звук · 0%",12,muted);root.addView(audioMeter);audioList=column();root.addView(audioList);renderAudio();
        root.addView(text("Во время работы сцены аудиомикс доступен локальным приложениям. Физический микрофон открывается только для включённого источника «Микрофон».",12,muted));
        TextView note=text("LSPosed: включите модуль и выберите целевые приложения только в его области действия. После запуска сцены перезапустите целевое приложение. Если камера не заменяется, откройте диагностику подключения.",12,muted);note.setPadding(0,dp(24),0,0);root.addView(note);
    }
    private void sceneSettings(){
        LinearLayout body=column();body.setPadding(dp(20),0,dp(20),0);
        body.addView(text("Размер сцены: ширина × высота (пиксели)",14,ink));
        EditText width=new EditText(this),height=new EditText(this);width.setInputType(2);height.setInputType(2);
        width.setText(""+Studio.WIDTH);height.setText(""+Studio.HEIGHT);body.addView(width);body.addView(height);
        body.addView(text("Высота окна предпросмотра",14,ink));
        SeekBar previewSize=new SeekBar(this);previewSize.setMin(160);previewSize.setMax(480);previewSize.setProgress(getPreferences(0).getInt("previewHeight",210));body.addView(previewSize);
        body.addView(text("FPS предпросмотра (1–60)",14,ink));
        EditText previewRate=new EditText(this);previewRate.setInputType(2);previewRate.setText(""+Studio.previewFps);body.addView(previewRate);
        body.addView(text("FPS сцены / выдачи кадров (1–30)",14,ink));
        EditText sceneRate=new EditText(this);sceneRate.setInputType(2);sceneRate.setText(""+Studio.FPS);body.addView(sceneRate);
        body.addView(text("Если приложение запрашивает другой размер",14,ink));
        String[] modes={FrameScale.FIT,FrameScale.CROP,FrameScale.STRETCH,FrameScale.FILL};
        String[] labels={"Вписать целиком · чёрные поля","Заполнить с обрезкой краёв","Растянуть","Вписать + мягкое фоновое заполнение"};
        Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        mode.setSelection(Math.max(0,Arrays.asList(modes).indexOf(Studio.scaleMode)));body.addView(mode);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Настройки кадра").setView(settingsScroll(body)).setPositiveButton("Применить",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
            try{int w=Integer.parseInt(width.getText().toString()),h=Integer.parseInt(height.getText().toString());
                if(w<128||h<128||w>1920||h>1920||(w&1)!=0||(h&1)!=0||w*(long)h>2073600)throw new IllegalArgumentException();
                int pf=Integer.parseInt(previewRate.getText().toString()),fps=Integer.parseInt(sceneRate.getText().toString());if(pf<1||pf>60||fps<1||fps>30)throw new IllegalArgumentException();
                Studio.previewFps=pf;Studio.FPS=fps;Studio.WIDTH=w;Studio.HEIGHT=h;Studio.scaleMode=modes[mode.getSelectedItemPosition()];Studio.save(this);getPreferences(0).edit().putInt("previewHeight",previewSize.getProgress()).apply();preview.getLayoutParams().height=dp(previewSize.getProgress());preview.requestLayout();preview.invalidate();dialog.dismiss();
            }catch(IllegalArgumentException e){Toast.makeText(this,"Проверьте размеры (чётные, 128–1920, до 2 Мп) и FPS (предпросмотр 1–60, сцена 1–30)",Toast.LENGTH_LONG).show();}
        }));dialog.show();
    }
    private ScrollView settingsScroll(View body){ScrollView scroll=new ScrollView(this);scroll.addView(body);return scroll;}
    private void showCameras(){
        try{android.hardware.camera2.CameraManager manager=getSystemService(android.hardware.camera2.CameraManager.class);String[] ids=manager.getCameraIdList();String[] names=new String[ids.length];
            for(int i=0;i<ids.length;i++){Integer facing=manager.getCameraCharacteristics(ids[i]).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);names[i]="Камера "+ids[i]+" · "+(facing!=null&&facing==0?"фронтальная":facing!=null&&facing==1?"задняя":"внешняя");}
            new AlertDialog.Builder(this).setTitle("Добавить камеру устройства").setItems(names,(d,index)->{
                add(new Layer("camera",names[index],ids[index]));
                Set<String> chosen=new HashSet<>();for(Layer l:Studio.layers)if(l.kind.equals("camera")&&l.visible)chosen.add(l.uri);
                if(chosen.size()>1&&android.os.Build.VERSION.SDK_INT>=30){try{boolean supported=false;for(Set<String> group:manager.getConcurrentCameraIds())if(group.containsAll(chosen))supported=true;
                    if(!supported)Toast.makeText(this,"Устройство не заявляет одновременную работу этой комбинации камер. Неиспользуемую камеру можно выключить в списке источников",Toast.LENGTH_LONG).show();
                }catch(Exception ignored){}}
            })
                .setMessage(ids.length==0?"Доступных камер нет":null).show();
        }catch(Exception e){Toast.makeText(this,"Не удалось получить камеры: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void startStudio(){
        if(Studio.audio.stream().anyMatch(l->l.enabled&&l.kind.equals("mic"))&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);return;}
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED&&Studio.layers.stream().anyMatch(l->l.kind.equals("camera"))){requestPermissions(new String[]{Manifest.permission.CAMERA},10);return;}
        try{startForegroundService(new Intent(this,StudioService.class));}catch(Exception e){new AlertDialog.Builder(this).setMessage(e.toString()).setPositiveButton("OK",null).show();}
    }
    public void onRequestPermissionsResult(int request,String[] permissions,int[] result){super.onRequestPermissionsResult(request,permissions,result);if((request==10||request==11)&&result.length>0&&result[0]==PackageManager.PERMISSION_GRANTED)startStudio();}
    private void addSource(){new AlertDialog.Builder(this).setTitle("Добавить источник").setItems(new String[]{"Изображение","Видеофайл (зациклено)","Камера устройства","Сетевой поток MJPEG"},(d,i)->{
        if(i<2){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(i==0?"image/*":"video/*").addCategory(Intent.CATEGORY_OPENABLE);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(pick,100+i);}
        if(i==2){if(Studio.running){Toast.makeText(this,"Остановите сцену перед добавлением камеры, чтобы применить разрешение фонового сервиса",Toast.LENGTH_LONG).show();return;}showCameras();}

        if(i==3){EditText url=new EditText(this);url.setSingleLine();url.setHint("http://192.168.1.10:8080/video");new AlertDialog.Builder(this).setTitle("HTTP(S) MJPEG поток").setView(url).setPositiveButton("Добавить",(a,n)->{
            String value=url.getText().toString().trim();Uri u=Uri.parse(value);if(!Arrays.asList("http","https").contains(u.getScheme())||u.getHost()==null){Toast.makeText(this,"Некорректный HTTP(S) URL",Toast.LENGTH_LONG).show();return;}
            add(new Layer("mjpeg","Сетевой поток",value));}).setNegativeButton("Отмена",null).show();}
    }).show();}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException e){Toast.makeText(this,"Файл может стать недоступен после перезапуска",Toast.LENGTH_LONG).show();}
        if(request==200){Studio.audio.add(new AudioLayer("file","Аудиофайл",uri.toString()));Studio.save(this);renderAudio();return;}
        add(new Layer(request==100?"image":"video",request==100?"Изображение":"Видеофайл",uri.toString()));}
    private void add(Layer l){Studio.layers.add(l);selected=l;Studio.save(this);renderList();}
    private void addAudio(){new AlertDialog.Builder(this).setTitle("Источник звука").setItems(new String[]{"Звук устройства · через LSPosed","Физический микрофон","Из интернета · HTTP(S)","Аудиофайл · зацикленно"},(dialog,index)->{
        if(Studio.running){Toast.makeText(this,"Остановите сцену перед добавлением аудиоисточника",Toast.LENGTH_LONG).show();return;}
        if(index==0){if(Studio.audio.stream().anyMatch(l->l.kind.equals("device"))){Toast.makeText(this,"Источник звука устройства уже добавлен",Toast.LENGTH_SHORT).show();return;}Studio.audio.add(new AudioLayer("device","Звук устройства",""));Toast.makeText(this,"Захватывается AudioTrack приложений в области LSPosed. Нативные аудиодвижки могут не поддерживаться",Toast.LENGTH_LONG).show();}
        if(index==1){if(Studio.audio.stream().anyMatch(l->l.kind.equals("mic"))){Toast.makeText(this,"Источник микрофона уже добавлен",Toast.LENGTH_SHORT).show();return;}Studio.audio.add(new AudioLayer("mic","Микрофон",""));}
        if(index==2){EditText url=new EditText(this);url.setHint("https://example.com/audio.mp3");new AlertDialog.Builder(this).setTitle("Прямой URL аудиопотока или файла").setView(url).setPositiveButton("Добавить",(d,n)->{String value=url.getText().toString().trim();Uri uri=Uri.parse(value);if(!Arrays.asList("http","https").contains(uri.getScheme())||uri.getHost()==null){Toast.makeText(this,"Нужен HTTP(S) URL",Toast.LENGTH_LONG).show();return;}Studio.audio.add(new AudioLayer("internet","Интернет-аудио",value));Studio.save(this);renderAudio();}).setNegativeButton("Отмена",null).show();return;}
        if(index==3){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),200);return;}
        Studio.save(this);renderAudio();
    }).show();}
    private void renderAudio(){if(audioList==null)return;audioList.removeAllViews();for(AudioLayer layer:Studio.audio){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);CheckBox enabled=new CheckBox(this);enabled.setChecked(layer.enabled);enabled.setOnCheckedChangeListener((button,on)->{if(on&&layer.kind.equals("mic")&&Studio.running){enabled.setChecked(false);Toast.makeText(this,"Остановите сцену, чтобы включить микрофон",Toast.LENGTH_LONG).show();return;}layer.enabled=on;Studio.save(this);});row.addView(enabled);
        TextView label=text(layer.name,14,ink);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));row.addView(button("⋮",()->{LinearLayout body=column();body.setPadding(dp(20),0,dp(20),0);EditText name=new EditText(this);name.setText(layer.name);body.addView(name);slider(body,"Громкость",Math.round(layer.gain*100),200,v->layer.gain=v/100f);new AlertDialog.Builder(this).setTitle("Источник звука").setView(body).setPositiveButton("Готово",(d,n)->{layer.name=name.getText().toString();Studio.save(this);renderAudio();}).setNeutralButton("Удалить",(d,n)->{Studio.audio.remove(layer);Studio.save(this);renderAudio();}).show();}));audioList.addView(row);}}
    private void renderList(){list.removeAllViews();if(Studio.layers.isEmpty()){TextView empty=text("Нет источников\nДобавьте камеру, видео или изображение",15,muted);empty.setPadding(dp(16),dp(24),dp(16),dp(24));list.addView(empty);return;}
        for(Layer l:Studio.layers){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);CheckBox check=new CheckBox(this);check.setChecked(l.visible);check.setOnCheckedChangeListener((b,on)->{l.visible=on;Studio.save(this);});row.addView(check);
            TextView title=text(l.name+"\n"+l.kind,15,l==selected?accent:ink);title.setPadding(dp(8),dp(10),0,dp(10));title.setOnClickListener(v->{selected=l;renderList();edit(l);});row.addView(title,new LinearLayout.LayoutParams(0,-2,1));row.addView(button("⋮",()->edit(l)));list.addView(row);}}
    private void edit(Layer l){selected=l;LinearLayout body=column();body.setPadding(dp(20),0,dp(20),0);EditText name=new EditText(this);name.setText(l.name);body.addView(name);
        body.addView(text("Масштабирование источника",14,ink));
        String[] modes={"inherit",FrameScale.FIT,FrameScale.CROP,FrameScale.STRETCH,FrameScale.FILL};
        Spinner fit=new Spinner(this);fit.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Как в настройках сцены","Вписать · сохранить весь кадр","Обрезать · заполнить без искажения","Растянуть","Вписать + фоновое заполнение"}));fit.setSelection(Math.max(0,Arrays.asList(modes).indexOf(l.scaleMode)));
        fit.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> a,View v,int position,long id){l.scaleMode=modes[position];Studio.save(MainActivity.this);}public void onNothingSelected(AdapterView<?> a){}});body.addView(fit);
        slider(body,"X",Math.round(l.x*100),100,v->l.x=v/100f);slider(body,"Y",Math.round(l.y*100),100,v->l.y=v/100f);
        slider(body,"Ширина",Math.round(l.width*100),100,v->l.width=Math.max(.05f,v/100f));slider(body,"Высота",Math.round(l.height*100),100,v->l.height=Math.max(.05f,v/100f));slider(body,"Непрозрачность",Math.round(l.alpha*100),100,v->l.alpha=v/100f);
        CheckBox mirror=new CheckBox(this);mirror.setText("Отразить горизонтально");mirror.setTextColor(ink);mirror.setChecked(l.mirror);mirror.setOnCheckedChangeListener((b,on)->l.mirror=on);body.addView(mirror);
        body.addView(button("Повернуть на 90°",()->l.rotation=(l.rotation+90)%360));
        body.addView(button("На передний план",()->{Studio.layers.remove(l);Studio.layers.add(l);Studio.save(this);renderList();}));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("Параметры слоя").setView(scroll).setPositiveButton("Готово",(d,n)->{l.name=name.getText().toString();Studio.save(this);renderList();})
            .setNeutralButton("Удалить",(d,n)->{Studio.layers.remove(l);Studio.save(this);renderList();}).show();}
    private interface Change{void set(int value);}
    private void slider(LinearLayout box,String label,int value,int max,Change change){TextView title=text(label+" · "+value+"%",12,muted);box.addView(title);SeekBar bar=new SeekBar(this);bar.setMax(max);bar.setProgress(value);bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){if(user){change.set(p);title.setText(label+" · "+p+"%");}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){Studio.save(MainActivity.this);}});box.addView(bar);}
    private final class Preview extends View {
        private final Paint paint=new Paint(3);private float oldX,oldY;private boolean multi;
        private final ScaleGestureDetector scale=new ScaleGestureDetector(MainActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            public boolean onScale(ScaleGestureDetector d){if(selected==null)return false;RectF r=viewport();float k=Math.max(.05f,Math.min(4f,selected.width*d.getScaleFactor()))/selected.width;
                float x=(d.getFocusX()-r.left)/r.width(),y=(d.getFocusY()-r.top)/r.height();selected.x=x-(x-selected.x)*k;selected.y=y-(y-selected.y)*k;selected.width*=k;selected.height*=k;invalidate();return true;}
        });
        Preview(){super(MainActivity.this);setBackgroundColor(Color.BLACK);}
        protected void onDraw(Canvas c){super.onDraw(c);Bitmap b=Studio.preview;if(b!=null){RectF rect=viewport();c.drawBitmap(b,null,rect,paint);
            if(selected!=null){int save=c.save();c.clipRect(rect);paint.setColor(accent);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));c.drawRect(rect.left+selected.x*rect.width(),rect.top+selected.y*rect.height(),rect.left+(selected.x+selected.width)*rect.width(),rect.top+(selected.y+selected.height)*rect.height(),paint);paint.setStyle(Paint.Style.FILL);c.restoreToCount(save);}}else{paint.setColor(muted);paint.setTextSize(dp(14));c.drawText("Запустите сцену для предпросмотра",dp(12),getHeight()/2f,paint);}}
        private RectF viewport(){float ratio=Studio.WIDTH/(float)Studio.HEIGHT;float w=getWidth(),h=w/ratio;if(h>getHeight()){h=getHeight();w=h*ratio;}return new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);}
        public boolean onTouchEvent(android.view.MotionEvent e){RectF r=viewport();if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&selected==null){float x=(e.getX()-r.left)/r.width(),y=(e.getY()-r.top)/r.height();for(int i=Studio.layers.size()-1;i>=0;i--){Layer l=Studio.layers.get(i);if(l.visible&&x>=l.x&&x<=l.x+l.width&&y>=l.y&&y<=l.y+l.height){selected=l;renderList();break;}}}
            if(selected==null)return false;scale.onTouchEvent(e);if(e.getPointerCount()>1){multi=true;return true;}if(multi){oldX=e.getX();oldY=e.getY();multi=false;}if(e.getAction()==MotionEvent.ACTION_DOWN){oldX=e.getX();oldY=e.getY();getParent().requestDisallowInterceptTouchEvent(true);return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){selected.x=Math.max(-selected.width+.02f,Math.min(.98f,selected.x+(e.getX()-oldX)/r.width()));selected.y=Math.max(-selected.height+.02f,Math.min(.98f,selected.y+(e.getY()-oldY)/r.height()));oldX=e.getX();oldY=e.getY();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);Studio.save(MainActivity.this);performClick();return true;}return true;}
        public boolean performClick(){super.performClick();return true;}
    }
}
