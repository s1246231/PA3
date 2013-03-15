import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;


public class CacheCoherenceSC {
  public static final int INITIAL_CACHE_VALUE=-99;
	
	public static final int TAG=0;
	public static final int MSI_STATE=1;
	
	public static final int MSI_M=0;
	public static final int MSI_S=1;
	public static final int MSI_I=2;
	
	public static final int L1_CACHE_HIT_COUNT=2;
	public static final int BUS_TRANSACTION_COUNT=20;
	public static final int MAIN_MEMORY_ACCESS_COUNT=200;
	
	
	//initialize cache and return hashmap with key as processor name and value as integer array containing tag values of memory locations
	public HashMap<String,int[][]> initializeCache(int cacheLineSize,int cacheLineCount){
		
		HashMap<String,int[][]> hm=new HashMap<String,int[][]>();
		
		for(int i=0;i<4;i++){
			int[][]  arr=new int[cacheLineCount][2];
			
			
			for(int j=0;j<arr.length;j++){
				for(int k=0;k<arr[j].length;k++){
					//Initialise array with INITIAL_CACHE_VALUE=-99 as default value 
					arr[j][k]=CacheCoherenceSC.INITIAL_CACHE_VALUE;
				}
			}
			hm.put("P"+i,arr);
		}
		
		return hm;
	}
	
	//to print cache
	public void printCache(HashMap<String,int[][]> hm){
		
		Iterator it = hm.entrySet().iterator();
		 while (it.hasNext()) {
			 Map.Entry pairs = (Map.Entry)it.next();
			 
			 System.out.println(pairs.getKey() + " = " );
			 int arr[][]=(int[][])pairs.getValue();
			 for(int i=0; i<arr.length;i++){
				 System.out.println(" arr["+i+"] Tag :"+arr[i][0]+" State :"+arr[i][1]);
			 }
				 
		 }
		
	}

	//method to read trace file entries	
	public void readTraceFile(int cacheLineSize,int cacheLineCount,HashMap<String,int[][]> hm,String fileName){
	
			BufferedReader br = null;
			int offset, index, tag;
			int readCacheHitProcessor0 = 0,readCacheHitProcessor1=0,readCacheHitProcessor2=0,readCacheHitProcessor3=0;
			int readCacheMissProcessor0=0,readCacheMissProcessor1=0,readCacheMissProcessor2=0,readCacheMissProcessor3=0;
			
			int writeCacheHitProcessor0=0,writeCacheHitProcessor1=0,writeCacheHitProcessor2=0,writeCacheHitProcessor3=0;
			int writeCacheMissProcessor0=0,writeCacheMissProcessor1=0,writeCacheMissProcessor2=0,writeCacheMissProcessor3=0;
			
			int privateCacheLineReadCount=0,privateCacheLineWriteCount=0;
			int coherenceCacheMissCount=0;
			
			int scLatencyProcessor0=0,scLatencyProcessor1=0,scLatencyProcessor2=0,scLatencyProcessor3=0;
			
			try {
	 			String currentLine;
				br = new BufferedReader(new FileReader(fileName)); 
				int i=0;
				String processorName;
				
				while ((currentLine = br.readLine()) != null) {
					i++;
//					System.out.println(currentLine);
					String[] splittedRowArr=currentLine.split("\\s+");
					CacheRowElement cre=new CacheRowElement();
					cre.processorName=splittedRowArr[0];
					cre.operationPerformed=splittedRowArr[1];
					cre.memoryAddress=Integer.parseInt(splittedRowArr[2]);
					
					int address=cre.memoryAddress;
					
					
					System.out.println("\n\nAddress["+i+"] : "+address);			
					offset=address%cacheLineSize;
					
					//Remove the offset from address
					address=address>>((int)(Math.log(cacheLineSize)/Math.log(2)));
					//Retrive index
					index=address%cacheLineCount;
					
					//Remove the offset from address
					address=address>>((int)(Math.log(cacheLineCount)/Math.log(2)));
					//Retrive index
					tag=address;
					
					System.out.println("Tag:"+tag+" Index:"+index+" Offset:"+offset);
					
					//add tag entry to corresponding processor and calculate cache hit and miss
					
					
					int[][] tagArrHm=hm.get(cre.processorName);
					
					
					//read operation
					if(cre.operationPerformed.equalsIgnoreCase("R")){
						//if tag matches then cache hit for read operation
						
						//if processor wants to read value and its present in local cache with up-to-date value (either Modified or Shared))
						//then its read hit and 
						//no need to check with other processors
						if(tagArrHm[index][CacheCoherenceSC.TAG]==tag &&
								(tagArrHm[index][CacheCoherenceSC.MSI_STATE]==CacheCoherenceSC.MSI_M || tagArrHm[index][CacheCoherenceSC.MSI_STATE]==CacheCoherenceSC.MSI_S)){
							
							//private cache line read counter i.e. cache lines that are only accessed by one processor
									if(tagArrHm[index][CacheCoherenceSC.MSI_STATE]==CacheCoherenceSC.MSI_M){
									privateCacheLineReadCount++;
									}
									
									//if read hit then add latency of 2 cycles to respective processor
									if(cre.processorName.equalsIgnoreCase("P0")){
										readCacheHitProcessor0++;
										scLatencyProcessor0+=L1_CACHE_HIT_COUNT;
									}else if(cre.processorName.equalsIgnoreCase("P1")){
										readCacheHitProcessor1++;
										scLatencyProcessor1+=L1_CACHE_HIT_COUNT;
									}else if(cre.processorName.equalsIgnoreCase("P2")){
										readCacheHitProcessor2++;
										scLatencyProcessor2+=L1_CACHE_HIT_COUNT;
									}else if(cre.processorName.equalsIgnoreCase("P3")){
										readCacheHitProcessor3++;
										scLatencyProcessor3+=L1_CACHE_HIT_COUNT;
									}else {
										System.out.println("Cache Hit error occured while read operation");
									}
							
							}
						// If cache miss occur while read operation then need to check if there are any other processors having Modified state of the 
						// Cache line then make that entry as Shared as well as current entry as Shared. 
						// Also here need to make sure that the other processor are having same block as M.  
						else{
								
								//read cache miss occurred and cache miss that are coherence misses i.e. accesses which miss in the cache 
								//because of other processors invalidating the cache line
								if(tagArrHm[index][CacheCoherenceSC.MSI_STATE]==CacheCoherenceSC.MSI_I){
									coherenceCacheMissCount++;
								}
								
								// to iterate over other processors
								Iterator it = hm.entrySet().iterator();
								 while (it.hasNext()) {
									 Map.Entry pairs = (Map.Entry)it.next();
									 if(!pairs.getKey().toString().equals(cre.processorName)){
										 int arr[][]=(int[][])pairs.getValue();
										 
										 if(tag==arr[index][CacheCoherenceSC.TAG] && 
												 arr[index][CacheCoherenceSC.MSI_STATE]==CacheCoherenceSC.MSI_M ){
											 
											 //making other processor status bit as Shared
											 arr[index][CacheCoherenceSC.MSI_STATE]=CacheCoherenceSC.MSI_S;
										 }
									 }
								 }
								// end of the iterator over other processors
								
								 //add latencies when its a read cache miss  (L1 cache hit 2 cycles are added because processor searches
								 //in the cache so it will consume cycles)
								 if(cre.processorName.equalsIgnoreCase("P0")){
									 	readCacheMissProcessor0++;
									 	scLatencyProcessor0+=L1_CACHE_HIT_COUNT;
									 	scLatencyProcessor0+=BUS_TRANSACTION_COUNT;
									 	scLatencyProcessor0+=MAIN_MEMORY_ACCESS_COUNT;
									}else if(cre.processorName.equalsIgnoreCase("P1")){
										readCacheMissProcessor1++;
										scLatencyProcessor1+=L1_CACHE_HIT_COUNT;
										scLatencyProcessor1+=BUS_TRANSACTION_COUNT;
									 	scLatencyProcessor1+=MAIN_MEMORY_ACCESS_COUNT;
									}else if(cre.processorName.equalsIgnoreCase("P2")){
										readCacheMissProcessor2++;
										scLatencyProcessor2+=L1_CACHE_HIT_COUNT;
										scLatencyProcessor2+=BUS_TRANSACTION_COUNT;
									 	scLatencyProcessor2+=MAIN_MEMORY_ACCESS_COUNT;
									}else if(cre.processorName.equalsIgnoreCase("P3")){
										readCacheMissProcessor3++;
										scLatencyProcessor3+=L1_CACHE_HIT_COUNT;
										scLatencyProcessor3+=BUS_TRANSACTION_COUNT;
									 	scLatencyProcessor3+=MAIN_MEMORY_ACCESS_COUNT;
									}else {
										System.out.println("Cache Miss error occured while read operation");
									}
								//change self entry of status bit as Shared
								tagArrHm[index][CacheCoherenceSC.TAG]=tag;
								tagArrHm[index][CacheCoherenceSC.MSI_STATE]=CacheCoherenceSC.MSI_S;
								}
					}
					//write operation
					else if(cre.operationPerformed.equalsIgnoreCase("W")){
						
						
						//add latencies for Shared operation
						if(tagArrHm[index][CacheCoherenceSC.TAG]==tag && tagArrHm[index][CacheCoherenceSC.MSI_STATE]==CacheCoherenceSC.MSI_S){
						if(cre.processorName.equalsIgnoreCase("P0")){
							writeCacheHitProcessor0++;
							scLatencyProcessor0+=L1_CACHE_HIT_COUNT;
							scLatencyProcessor0+=BUS_TRANSACTION_COUNT;
						}else if(cre.processorName.equalsIgnoreCase("P1")){
							writeCacheHitProcessor1++;
							scLatencyProcessor1+=L1_CACHE_HIT_COUNT;
							scLatencyProcessor1+=BUS_TRANSACTION_COUNT;
						}else if(cre.processorName.equalsIgnoreCase("P2")){
							writeCacheHitProcessor2++;
							scLatencyProcessor2+=L1_CACHE_HIT_COUNT;
							scLatencyProcessor2+=BUS_TRANSACTION_COUNT;
						}else if(cre.processorName.equalsIgnoreCase("P3")){
							writeCacheHitProcessor3++;
							scLatencyProcessor3+=L1_CACHE_HIT_COUNT;
							scLatencyProcessor3+=BUS_TRANSACTION_COUNT;
						}else {
							System.out.println("Cache Hit error occured while write operation");
						}
						}
						//----------end of latency for shared status bit
						
						
						//cache hit and update other processors that the current entry is now updated ---- by invalidating the tag
						//of other processors(by respective negative value)
						
		
						//check if tag value matches then its cache hit modify status bit as Modified and also update other processor status bits 
						// as Invalid (for which tag value matches)
						if(tagArrHm[index][CacheCoherenceSC.TAG]==tag && tagArrHm[index][CacheCoherenceSC.MSI_STATE]!=CacheCoherenceSC.MSI_S){

							//private cache line write counter i.e. cache lines that are only accessed by one processor
							if(tagArrHm[index][CacheCoherenceSC.MSI_STATE]==CacheCoherenceSC.MSI_M){
							privateCacheLineWriteCount++;
							}
							
							//add latency of cache hit for respective processor
							if(cre.processorName.equalsIgnoreCase("P0")){
								writeCacheHitProcessor0++;
								scLatencyProcessor0+=L1_CACHE_HIT_COUNT;
							}else if(cre.processorName.equalsIgnoreCase("P1")){
								writeCacheHitProcessor1++;
								scLatencyProcessor1+=L1_CACHE_HIT_COUNT;
							}else if(cre.processorName.equalsIgnoreCase("P2")){
								writeCacheHitProcessor2++;
								scLatencyProcessor2+=L1_CACHE_HIT_COUNT;
							}else if(cre.processorName.equalsIgnoreCase("P3")){
								writeCacheHitProcessor3++;
								scLatencyProcessor3+=L1_CACHE_HIT_COUNT;
							}else {
								System.out.println("Cache Hit error occured while write operation");
							}
							
							//update self status as Modify
							tagArrHm[index][CacheCoherenceSC.MSI_STATE]=CacheCoherenceSC.MSI_M;
							// to iterate over other processors and modify respective status bit as Invalid
							Iterator it = hm.entrySet().iterator();
							 while (it.hasNext()) {
								 Map.Entry pairs = (Map.Entry)it.next();
//								 totalMemoryAccesses++;
								 if(!pairs.getKey().toString().equals(cre.processorName)){
									 int arr[][]=(int[][])pairs.getValue();
									 
									 if(tag==arr[index][CacheCoherenceSC.TAG]){
										//here no need to check status bits of other processor as all respective become Invalid
										//making other processor status bit as Shared
										arr[index][CacheCoherenceSC.MSI_STATE]=CacheCoherenceSC.MSI_I;
									 }
								 }
							 }
							// end of the iterator over other processors
							
						}
						//write cache miss
						else{
							
							//write cache miss occurred and cache miss that are coherence misses i.e. accesses which miss in the cache 
							//because of other processors invalidating the cache line
							if(tagArrHm[index][CacheCoherenceSC.MSI_STATE]==CacheCoherenceSC.MSI_I){
								coherenceCacheMissCount++;
							}
							
							if(cre.processorName.equalsIgnoreCase("P0")){
								writeCacheMissProcessor0++;
								scLatencyProcessor0+=L1_CACHE_HIT_COUNT;
							 	scLatencyProcessor0+=BUS_TRANSACTION_COUNT;
							 	scLatencyProcessor0+=MAIN_MEMORY_ACCESS_COUNT;
							}else if(cre.processorName.equalsIgnoreCase("P1")){
								writeCacheMissProcessor1++;
								scLatencyProcessor1+=L1_CACHE_HIT_COUNT;
							 	scLatencyProcessor1+=BUS_TRANSACTION_COUNT;
							 	scLatencyProcessor1+=MAIN_MEMORY_ACCESS_COUNT;
							}else if(cre.processorName.equalsIgnoreCase("P2")){
								writeCacheMissProcessor2++;
								scLatencyProcessor2+=L1_CACHE_HIT_COUNT;
							 	scLatencyProcessor2+=BUS_TRANSACTION_COUNT;
							 	scLatencyProcessor2+=MAIN_MEMORY_ACCESS_COUNT;
							}else if(cre.processorName.equalsIgnoreCase("P3")){
								writeCacheMissProcessor3++;
								scLatencyProcessor3+=L1_CACHE_HIT_COUNT;
							 	scLatencyProcessor3+=BUS_TRANSACTION_COUNT;
							 	scLatencyProcessor3+=MAIN_MEMORY_ACCESS_COUNT;
							}else {
								System.out.println("Cache Miss error occured while write operation");
							}
							//caching for write miss 
							tagArrHm[index][CacheCoherenceSC.TAG]=tag;
							//update self status as M
							tagArrHm[index][CacheCoherenceSC.MSI_STATE]=CacheCoherenceSC.MSI_M;
							// to iterate over other processors and modify respective status bit as Invalid
							Iterator it = hm.entrySet().iterator();
							 while (it.hasNext()) {
								 Map.Entry pairs = (Map.Entry)it.next();
								 if(!pairs.getKey().toString().equals(cre.processorName)){
									 int arr[][]=(int[][])pairs.getValue();
									 
									 if(tag==arr[index][CacheCoherenceSC.TAG]){
										//here no need to check status of other processor as all respective become Invalid
										//making other processor status bit as Shared
										arr[index][CacheCoherenceSC.MSI_STATE]=CacheCoherenceSC.MSI_I;
									 }
								 }
							 }
							// end of the iterator over other processors
						}
						
					}
					
				}	
				this.printCache(hm);
				System.out.println("SC latency for Processor 0 :: "+scLatencyProcessor0);
				System.out.println("SC latency for Processor 1 :: "+scLatencyProcessor1);
				System.out.println("SC latency for Processor 2 :: "+scLatencyProcessor2);
				System.out.println("SC latency for Processor 3 :: "+scLatencyProcessor3);
				int totalScLatency=scLatencyProcessor0+scLatencyProcessor1+scLatencyProcessor2+scLatencyProcessor3;
				System.out.println("SC Total latency :: "+totalScLatency);
				
				
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (br != null)br.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}		
}
	
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		int cacheLineCount,cacheLineSize;
		String traceFileName;
		
		  Scanner in = new Scanner(System.in);
		 
		  System.out.print("Enter number of lines in the cache :: ");
//	      cacheLineCount =in.nextInt();
		  cacheLineCount=128;
	      	      
	      System.out.print("Enter size of the cache line :: ");
//	      cacheLineSize =in.nextInt();
	      cacheLineSize=4;
	      
//	      System.out.print("Enter name of the trace file (e.g trace1) :: ");
//	      traceFileName =in.next();	      
//	      traceFileName=args[2];
	      
	      HashMap<String,int[][]> hm=new HashMap<String,int[][]>();
	      CacheCoherenceSC cs=new CacheCoherenceSC();
	      hm=cs.initializeCache(cacheLineSize,cacheLineCount);
	      //read trace file
//	      String fileName=traceFileName;
	      String fileName="C:\\Users\\Shree\\junoworkspace\\CacheCoherence\\src\\testTrace1";
//	      String fileName="C:\\Users\\Shree\\junoworkspace\\CacheCoherence\\src\\trace1";
	      cs.readTraceFile(cacheLineSize,cacheLineCount,hm,fileName);

	}

}
