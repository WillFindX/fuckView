package ml.qingsu.fuckview.utils.dumper;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;

import com.jrummyapps.android.shell.Shell;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import ml.qingsu.fuckview.ui.activities.MainActivity;

/**
 * Created by w568w on 2017-7-12.
 */

public class ViewDumper {
    public static final int NO_PARENT = -1;

    public static final class ViewItem {
        public String simpleClassName;
        public Point bounds;
        public Point wh;
        public int id;
        public String text;
        public int parentId;
        public int level;

        @Override
        public String toString() {
            return String.format(Locale.CHINA, "%s,%d,%d,%d,%d", simpleClassName, bounds.x, bounds.y, wh.x, wh.y);
        }
    }

    public static synchronized ArrayList<ViewItem> parseCurrentView() {
//        File f = new File("/mnt/sdcard/dump.xml");
//        if (f.exists()) f.delete();


        Shell.SU.run("uiautomator dump /mnt/sdcard/dump.xml");
        String xml = MainActivity.readFile("dump.xml").replace("\n", "");
        ArrayList<ViewItem> itemList = new ArrayList<>();
        ViewItem temp;
        XmlPullParser parser = Xml.newPullParser();
        try {
            parser.setInput(new StringReader(xml));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("node".equals(tagName)) {
                            temp = new ViewItem();
                            //读类名
                            String[] className = parser.getAttributeValue(null, "class").split("\\.");
                            temp.simpleClassName = className[className.length - 1];
                            //读坐标
                            String bounds = parser.getAttributeValue(null, "bounds");
                            String[] tempN = bounds.replaceFirst("\\[", "").split("\\]\\[");
                            String[] point = tempN[0].split(",");
                            if (point.length == 2) {
                                temp.bounds = new Point(Integer.valueOf(point[0]), Integer.valueOf(point[1]));
                            } else {
                                temp.bounds = new Point();
                            }
                            //读宽高
                            point = bounds.split("\\]\\[")[1].split(",");
                            if (point.length == 2) {
                                temp.wh = new Point(Integer.valueOf(point[0]) - temp.bounds.x, Integer.valueOf(point[1].replaceFirst("\\]", "")) - temp.bounds.y);
                            } else {
                                temp.wh = new Point();
                            }
                            //其他杂类信息
                            temp.id = itemList.size();
                            temp.level = parser.getDepth();
                            ViewItem vi = getLastTopLevelNode(itemList, temp.level - 1);
                            temp.parentId = (vi == null ? NO_PARENT : vi.id);
                            itemList.add(temp);
                        }
                        break;
                    default:
                        break;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return itemList;
    }

    private static ViewItem getLastTopLevelNode(ArrayList<ViewItem> al, int depth) {
        ArrayList<ViewItem> copy = new ArrayList<>(al);
        Collections.reverse(copy);
        for (ViewItem tn : copy) {
            if (tn.level == depth) {
                return tn;
            }
        }
        return null;
    }

    public static synchronized ArrayList<ViewItem> parseCurrentViewAbove16(Context context) {
        if (DumperService.getInstance() == null) {
            ServiceUtils.openSetting(context);
            return null;
        }
        AccessibilityNodeInfo root = DumperService.getInstance().getRootInActiveWindow();
        itemList = new ArrayList<>();
        if (root == null) {
            return itemList;
        }
        parseChild(root, 2);
        return itemList;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private static void parseChild(AccessibilityNodeInfo parent, int depth) {
        int childCount = parent == null ? 0 : parent.getChildCount();
        itemList.add(nodeInfoToViewItem(parent, depth));
        for (int i = 0; i < childCount; i++) {
            parseChild(parent.getChild(i), depth + 1);
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private synchronized static ViewItem nodeInfoToViewItem(@Nullable AccessibilityNodeInfo node, int depth) {
        ViewItem viewItem = new ViewItem();


        viewItem.id = id++;
        viewItem.level = depth;
        ViewItem topLevelNode = getLastTopLevelNode(itemList, depth - 1);
        viewItem.parentId = (topLevelNode == null ? NO_PARENT : topLevelNode.id);
        if (node == null) {
            return viewItem;
        }
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        viewItem.bounds = new Point(rect.left, rect.top);
        viewItem.wh = new Point(rect.width(), rect.height());
        viewItem.simpleClassName = node.getClassName().toString();
        if (viewItem.simpleClassName.contains(".")) {
            viewItem.simpleClassName = viewItem.simpleClassName.substring(viewItem.simpleClassName.lastIndexOf("."));
        }
        return viewItem;
    }

    private static int id = 0;
    private static ArrayList<ViewItem> itemList;

}
