package toc2.toc2;

public class MetaDataHelper {

    static public int[] parseMetaDataString(String data){
        String[] elements = data.split("\\s");
        int[] values = new int[elements.length];
        for(int i = 0; i < elements.length; i++){
            values[i] = Integer.parseInt(elements[i]);
        }
        return values;
    }

    static public String createMetaDataString(int[] values){
        StringBuilder mdata = new StringBuilder();
        for(int v : values){
            mdata.append(v).append(" ");
        }
        return mdata.toString();
    }
}
