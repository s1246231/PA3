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

public class CacheCoherenceTSO {
  public static final int INITIAL_CACHE_VALUE = -99;
	public static final int INITIAL_WRITE_BUFFER_VALUE = -999;
	public static final int INITIAL_BLOCK_VALUE = -9;

	public static final int TAG = 0;
	public static final int MSI_STATE = 1;

	public static final int MSI_M = 0;
	public static final int MSI_S = 1;
	public static final int MSI_I = 2;

	public static final int READ_BYPASS_LATENCY_COUNT = 1;
	public static final int L1_CACHE_HIT_LATENCY_COUNT = 2;
	public static final int BUS_TRANSACTION_LATENCY_COUNT = 20;
	public static final int MAIN_MEMORY_ACCESS_LATENCY_COUNT = 200;

	public static final int TSO_BLOCK_MEMORY_ADDRESS = 0;
	public static final int TSO_PROCESSOR_LATENCYS = 1;

	int globalProcessorCycleCount = 0;
	int blockMemoryAddressCnt = 0;

	// initialize cache and return hashmap with key as processor name and value
	// as integer array containing tag values of memory locations
	public HashMap<String, int[][]> initializeCache(int cacheLineSize,
			int cacheLineCount) {

		HashMap<String, int[][]> hm = new HashMap<String, int[][]>();

		for (int i = 0; i < 4; i++) {
			int[][] arr = new int[cacheLineCount][2];

			for (int j = 0; j < arr.length; j++) {
				for (int k = 0; k < arr[j].length; k++) {
					// Initialise array with INITIAL_CACHE_VALUE=-99 as default
					// value
					arr[j][k] = CacheCoherenceTSO.INITIAL_CACHE_VALUE;
				}
			}
			hm.put("P" + i, arr);
		}

		return hm;
	}

	// initialize write buffer
	public HashMap<String, int[][]> initializeWriteBuffer(int writeBufferSize) {

		HashMap<String, int[][]> hmWriteBuffer = new HashMap<String, int[][]>();

		for (int i = 0; i < 4; i++) {
			int[][] arrWriteBuffer = new int[writeBufferSize][2];
			for (int j = 0; j < arrWriteBuffer.length; j++) {
				for (int k = 0; k < arrWriteBuffer[j].length; k++) {
					// Initialise array with INITIAL_CACHE_VALUE=-99 as default
					// value
					arrWriteBuffer[j][k] = CacheCoherenceTSO.INITIAL_WRITE_BUFFER_VALUE;
				}
			}
			hmWriteBuffer.put("P" + i, arrWriteBuffer);
		}

		return hmWriteBuffer;
	}

	// if returns true then memory address is present in the array and its a
	// "read bypass"
	// if returns false then memory address is absent in the array and we can
	// proceed with read operation
	public boolean blockMemoryStatus(int[][] blockMemoryArrHm,
			int blockMemoryAddress) {
		for (int b = 0; b < blockMemoryArrHm.length; b++) {
			if (blockMemoryArrHm[b][TSO_BLOCK_MEMORY_ADDRESS] == blockMemoryAddress) {
				// block memory address is present in the array
				return true;
			}
		}
		// block memory address is absent in the array
		return false;
	}

	// to print cache
	public void printCache(HashMap<String, int[][]> hm) {

		Iterator it = hm.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pairs = (Map.Entry) it.next();

			System.out.println(pairs.getKey() + " = ");
			int arr[][] = (int[][]) pairs.getValue();
			for (int i = 0; i < arr.length; i++) {
				System.out.println(" arr[" + i + "] Tag :" + arr[i][0]
						+ " State :" + arr[i][1]);
			}

		}

	}

	// to print write buffer
	public void printWriteBuffer(HashMap<String, int[][]> hmWriteBuffer) {

		Iterator it = hmWriteBuffer.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pairs = (Map.Entry) it.next();

			System.out.println(pairs.getKey() + " = ");
			int arrWriteBuffer[][] = (int[][]) pairs.getValue();
			for (int i = 0; i < arrWriteBuffer.length; i++) {
				System.out.println(" arrWriteBuffer[" + i
						+ "] Block memory address::"
						+ arrWriteBuffer[i][TSO_BLOCK_MEMORY_ADDRESS]
						+ " Latency ::"
						+ arrWriteBuffer[i][TSO_PROCESSOR_LATENCYS]);
			}
		}

	}

	// method to read trace file entries
	public void readTraceFile(int cacheLineSize, int cacheLineCount,
			HashMap<String, int[][]> hm, String fileName,
			HashMap<String, int[][]> hmWriteBuffer, int writeBufferSize,
			int retireAtN) {

		BufferedReader br = null;
		int offset, index, tag;
		int readCacheHitProcessor0 = 0, readCacheHitProcessor1 = 0, readCacheHitProcessor2 = 0, readCacheHitProcessor3 = 0;
		int readCacheMissProcessor0 = 0, readCacheMissProcessor1 = 0, readCacheMissProcessor2 = 0, readCacheMissProcessor3 = 0;

		int writeCacheHitProcessor0 = 0, writeCacheHitProcessor1 = 0, writeCacheHitProcessor2 = 0, writeCacheHitProcessor3 = 0;
		int writeCacheMissProcessor0 = 0, writeCacheMissProcessor1 = 0, writeCacheMissProcessor2 = 0, writeCacheMissProcessor3 = 0;

		int privateCacheLineReadCount = 0, privateCacheLineWriteCount = 0;
		int coherenceCacheMissCount = 0;

		int tsoLatencyProcessor0 = 0, tsoLatencyProcessor1 = 0, tsoLatencyProcessor2 = 0, tsoLatencyProcessor3 = 0;
		
		int currentWriteCompletionLatencyCount=0, previousWriteCompletionLatencyCount=0;

		try {
			String currentLine;
			br = new BufferedReader(new FileReader(fileName));
			int i = 0;
			String processorName;

			while ((currentLine = br.readLine()) != null) {
				i++;
				// System.out.println(currentLine);
				String[] splittedRowArr = currentLine.split("\\s+");
				CacheRowElement cre = new CacheRowElement();
				cre.processorName = splittedRowArr[0];
				cre.operationPerformed = splittedRowArr[1];
				cre.memoryAddress = Integer.parseInt(splittedRowArr[2]);

				int address = cre.memoryAddress;
				// memory address after removing offset
				int blockMemoryAddress = INITIAL_BLOCK_VALUE;

				System.out.println("\n\nAddress[" + i + "] : " + address);
				offset = address % cacheLineSize;

				// Remove the offset from address
				address = address >> ((int) (Math.log(cacheLineSize) / Math
						.log(2)));
				blockMemoryAddress = address;
				// Retrive index
				index = address % cacheLineCount;

				// Remove the offset from address
				address = address >> ((int) (Math.log(cacheLineCount) / Math
						.log(2)));
				// Retrive index
				tag = address;

				System.out.println("Tag:" + tag + " Index:" + index
						+ " Offset:" + offset);

				// add tag entry to corresponding processor and calculate cache
				// hit and miss

				int[][] tagArrHm = hm.get(cre.processorName);

				// read operation
				if (cre.operationPerformed.equalsIgnoreCase("R")) {

					// get write buffer array of current processor
					int[][] blockMemoryArrHm = hmWriteBuffer.get(cre.processorName);
					// if returns true then memory address is present in the array and its a "read bypass" then only add read bypass
					// latency and no need to access the cache if returns false then memory address is absent in the
					// array and we can proceed with read operation
					boolean getBlockMemoryStatus = this.blockMemoryStatus(blockMemoryArrHm, cre.memoryAddress);
					if (getBlockMemoryStatus == true) {
						globalProcessorCycleCount += READ_BYPASS_LATENCY_COUNT;
					} else {

						// if write hit or miss then increase global cpu cycle count by L1 cache hit cycles because it will take 
						// processor cycles (here 2) to check in the cache line
						globalProcessorCycleCount += L1_CACHE_HIT_LATENCY_COUNT;

						// if tag matches then cache hit for read operation

						// if processor wants to read value and its present in local cache with up-to-date value (either Modified or
						// Shared)) then its read hit and no need to check with other processors
						if (tagArrHm[index][CacheCoherenceTSO.TAG] == tag
								&& (tagArrHm[index][CacheCoherenceTSO.MSI_STATE] == CacheCoherenceTSO.MSI_M || tagArrHm[index][CacheCoherenceTSO.MSI_STATE] == CacheCoherenceTSO.MSI_S)) {

							// private cache line read counter i.e. cache lines that are only accessed by one processor
							if (tagArrHm[index][CacheCoherenceTSO.MSI_STATE] == CacheCoherenceTSO.MSI_M) {
								privateCacheLineReadCount++;
							}

							if (cre.processorName.equalsIgnoreCase("P0")) {
								readCacheHitProcessor0++;
							} else if (cre.processorName.equalsIgnoreCase("P1")) {
								readCacheHitProcessor1++;
							} else if (cre.processorName.equalsIgnoreCase("P2")) {
								readCacheHitProcessor2++;
							} else if (cre.processorName.equalsIgnoreCase("P3")) {
								readCacheHitProcessor3++;
							} else {
								System.out
										.println("Cache Hit error occured while read operation");
							}

						}
						// If cache miss occur while read operation then need to
						// check if there are any other processors having
						// Modified state of the
						// Cache line then make that entry as Shared as well as
						// current entry as Shared.
						// Also here need to make sure that the other processor
						// are having same block as M.
						else {

							// its a read miss so it will check with other
							// processors and then access to memory
							// add latencies when its a read cache miss (L1
							// cache hit 2 cycles are added because processor
							// searches
							// in the cache so it will consume cycles)
							globalProcessorCycleCount += BUS_TRANSACTION_LATENCY_COUNT;
							globalProcessorCycleCount += MAIN_MEMORY_ACCESS_LATENCY_COUNT;

							// read cache miss occurred and cache miss that are
							// coherence misses i.e. accesses which miss in the
							// cache
							// because of other processors invalidating the
							// cache line
							if (tagArrHm[index][CacheCoherenceTSO.MSI_STATE] == CacheCoherenceTSO.MSI_I) {
								coherenceCacheMissCount++;
							}

							// to iterate over other processors
							Iterator it = hm.entrySet().iterator();
							while (it.hasNext()) {
								Map.Entry pairs = (Map.Entry) it.next();
								if (!pairs.getKey().toString()
										.equals(cre.processorName)) {
									int arr[][] = (int[][]) pairs.getValue();

									if (tag == arr[index][CacheCoherenceTSO.TAG]
											&& arr[index][CacheCoherenceTSO.MSI_STATE] == CacheCoherenceTSO.MSI_M) {

										// making other processor status bit as
										// Shared
										arr[index][CacheCoherenceTSO.MSI_STATE] = CacheCoherenceTSO.MSI_S;
									}
								}
							}
							// end of the iterator over other processors

							if (cre.processorName.equalsIgnoreCase("P0")) {
								readCacheMissProcessor0++;
							} else if (cre.processorName.equalsIgnoreCase("P1")) {
								readCacheMissProcessor1++;
							} else if (cre.processorName.equalsIgnoreCase("P2")) {
								readCacheMissProcessor2++;
							} else if (cre.processorName.equalsIgnoreCase("P3")) {
								readCacheMissProcessor3++;
							} else {
								System.out
										.println("Cache Miss error occured while read operation");
							}
							// change self entry of status bit as Shared
							tagArrHm[index][CacheCoherenceTSO.TAG] = tag;
							tagArrHm[index][CacheCoherenceTSO.MSI_STATE] = CacheCoherenceTSO.MSI_S;
						}
					}
				}
				// write operation
				else if (cre.operationPerformed.equalsIgnoreCase("W")) {
					
					globalProcessorCycleCount += L1_CACHE_HIT_LATENCY_COUNT;

					// get write buffer array of current processor
					int[][] blockMemoryArrHm = hmWriteBuffer.get(cre.processorName);

					// check if buffer id not full then only add the element else drain the buffer according to retire-at-N write buffer removal policy policy
					if (blockMemoryAddressCnt <= (writeBufferSize)) {
						blockMemoryArrHm[blockMemoryAddressCnt][TSO_BLOCK_MEMORY_ADDRESS] = cre.memoryAddress;
						blockMemoryAddressCnt++;

						// buffer size reached to retire-at-N count so start processing the first element with FIFO 
						// start applying retire at N write buffer removal policy
						if (blockMemoryAddressCnt >= retireAtN) {
							
							// add latencies for Shared operation
							if (tagArrHm[index][CacheCoherenceSC.TAG] == tag
									&& tagArrHm[index][CacheCoherenceSC.MSI_STATE] == CacheCoherenceSC.MSI_S) {
								globalProcessorCycleCount += BUS_TRANSACTION_LATENCY_COUNT;
							}

							// cache hit and update other processors that the current entry is now updated ---- by invalidating the tag
							// of other processors(by respective negative value) check if tag value matches then its cache hit
							// modify status bit as Modified and also update other processor status bits as Invalid (for which tag value matches)
							if (tagArrHm[index][CacheCoherenceTSO.TAG] == tag
									&& tagArrHm[index][CacheCoherenceTSO.MSI_STATE] != CacheCoherenceTSO.MSI_S) {
								
								if(currentWriteCompletionLatencyCount<=globalProcessorCycleCount && blockMemoryAddressCnt>1){
									currentWriteCompletionLatencyCount=0;
									previousWriteCompletionLatencyCount=0;
									
									for (int j=0;j<blockMemoryArrHm.length-1;j++){
										blockMemoryArrHm[j]=blockMemoryArrHm[j+1];
									}
									blockMemoryArrHm[blockMemoryArrHm.length-1][TSO_BLOCK_MEMORY_ADDRESS]=INITIAL_WRITE_BUFFER_VALUE;
									blockMemoryArrHm[blockMemoryArrHm.length-1][TSO_PROCESSOR_LATENCYS]=INITIAL_WRITE_BUFFER_VALUE;
									blockMemoryAddressCnt--;
								}
								
								currentWriteCompletionLatencyCount=previousWriteCompletionLatencyCount+globalProcessorCycleCount+MAIN_MEMORY_ACCESS_LATENCY_COUNT;
								previousWriteCompletionLatencyCount=currentWriteCompletionLatencyCount-globalProcessorCycleCount;
								blockMemoryArrHm[blockMemoryAddressCnt-1][TSO_PROCESSOR_LATENCYS] = currentWriteCompletionLatencyCount;

								// private cache line write counter i.e. cache
								// lines that are only accessed by one processor
								if (tagArrHm[index][CacheCoherenceTSO.MSI_STATE] == CacheCoherenceTSO.MSI_M) {
									privateCacheLineWriteCount++;
								}

								// add latency of cache hit for respective
								// processor
								if (cre.processorName.equalsIgnoreCase("P0")) {
									writeCacheHitProcessor0++;
								} else if (cre.processorName.equalsIgnoreCase("P1")) {
									writeCacheHitProcessor1++;
								} else if (cre.processorName.equalsIgnoreCase("P2")) {
									writeCacheHitProcessor2++;
								} else if (cre.processorName.equalsIgnoreCase("P3")) {
									writeCacheHitProcessor3++;
								} else {
									System.out
											.println("Cache Hit error occured while write operation");
								}

								// update self status as Modify
								tagArrHm[index][CacheCoherenceTSO.MSI_STATE] = CacheCoherenceTSO.MSI_M;
								// to iterate over other processors and modify respective status bit as Invalid
								Iterator it = hm.entrySet().iterator();
								while (it.hasNext()) {
									Map.Entry pairs = (Map.Entry) it.next();
									// totalMemoryAccesses++;
									if (!pairs.getKey().toString().equals(cre.processorName)) {
										int arr[][] = (int[][]) pairs.getValue();

										if (tag == arr[index][CacheCoherenceTSO.TAG]) {
											// here no need to check status bits of other processor as all respective become Invalid
											//making other processor status bit as Shared
											arr[index][CacheCoherenceTSO.MSI_STATE] = CacheCoherenceTSO.MSI_I;
										}
									}
								}
								// end of the iterator over other processors

							}
							// write cache miss
							else {

								// write miss so add global processor latency count with bus transaction and access to memory latency count
//								globalProcessorCycleCount += L1_CACHE_HIT_LATENCY_COUNT;
								globalProcessorCycleCount += BUS_TRANSACTION_LATENCY_COUNT;
								
								//if current count is less then write complete remove element from array and reset counters
								if(currentWriteCompletionLatencyCount<=globalProcessorCycleCount && blockMemoryAddressCnt>1){
									currentWriteCompletionLatencyCount=0;
									previousWriteCompletionLatencyCount=0;
									
									for (int j=0;j<blockMemoryArrHm.length-1;j++){
										blockMemoryArrHm[j]=blockMemoryArrHm[j+1];
									}
									blockMemoryArrHm[blockMemoryArrHm.length-1][TSO_BLOCK_MEMORY_ADDRESS]=INITIAL_WRITE_BUFFER_VALUE;
									blockMemoryArrHm[blockMemoryArrHm.length-1][TSO_PROCESSOR_LATENCYS]=INITIAL_WRITE_BUFFER_VALUE;
									blockMemoryAddressCnt--;
								}
								
								currentWriteCompletionLatencyCount=previousWriteCompletionLatencyCount+globalProcessorCycleCount+MAIN_MEMORY_ACCESS_LATENCY_COUNT;
								previousWriteCompletionLatencyCount=currentWriteCompletionLatencyCount-globalProcessorCycleCount;
								blockMemoryArrHm[blockMemoryAddressCnt-1][TSO_PROCESSOR_LATENCYS] = currentWriteCompletionLatencyCount;
								
								// write cache miss occurred and cache miss that are coherence misses i.e. accesses which miss in the cache
								// because of other processors invalidating the cache line
								if (tagArrHm[index][CacheCoherenceTSO.MSI_STATE] == CacheCoherenceTSO.MSI_I) {
									coherenceCacheMissCount++;
								}

								if (cre.processorName.equalsIgnoreCase("P0")) {
									writeCacheMissProcessor0++;
									
								} else if (cre.processorName.equalsIgnoreCase("P1")) {
									writeCacheMissProcessor1++;
									
								} else if (cre.processorName.equalsIgnoreCase("P2")) {
									writeCacheMissProcessor2++;
									
								} else if (cre.processorName.equalsIgnoreCase("P3")) {
									writeCacheMissProcessor3++;
									
								} else {
									System.out
											.println("Cache Miss error occured while write operation");
								}
								// caching for write miss
								tagArrHm[index][CacheCoherenceTSO.TAG] = tag;
								// update self status as M
								tagArrHm[index][CacheCoherenceTSO.MSI_STATE] = CacheCoherenceTSO.MSI_M;
								// to iterate over other processors and modify
								// respective status bit as Invalid
								Iterator it = hm.entrySet().iterator();
								while (it.hasNext()) {
									Map.Entry pairs = (Map.Entry) it.next();
									if (!pairs.getKey().toString()
											.equals(cre.processorName)) {
										int arr[][] = (int[][]) pairs
												.getValue();

										if (tag == arr[index][CacheCoherenceTSO.TAG]) {
											// here no need to check status of other processor as all respective become Invalid making other processor status bit as Shared
											arr[index][CacheCoherenceTSO.MSI_STATE] = CacheCoherenceTSO.MSI_I;
										}
									}
								}
								// end of the iterator over other processors
							}
						
						}
					} else if (blockMemoryAddressCnt < writeBufferSize
							&& blockMemoryAddressCnt > retireAtN) {
						blockMemoryArrHm[blockMemoryAddressCnt][TSO_BLOCK_MEMORY_ADDRESS] = cre.memoryAddress;
						blockMemoryArrHm[blockMemoryAddressCnt][TSO_PROCESSOR_LATENCYS] = 0;
						blockMemoryAddressCnt++;
					}
					// buffer is full so stall the processor until buffer is drained drain it according to retire-at-N write buffer removal policy
					else if (blockMemoryAddressCnt == writeBufferSize
							&& blockMemoryAddressCnt > retireAtN) {

					} else {
						System.out.println("buffer is full !!");
					}

				}

			}
			this.printCache(hm);
			// this.printWriteBuffer(hmWriteBuffer);
			System.out.println("global latency count::"+ globalProcessorCycleCount);
			System.out.println("===========================");
			System.out.println("TSO latency for Processor 0 :: "+ tsoLatencyProcessor0);
			System.out.println("TSO latency for Processor 1 :: "+ tsoLatencyProcessor1);
			System.out.println("TSO latency for Processor 2 :: "+ tsoLatencyProcessor2);
			System.out.println("TSO latency for Processor 3 :: "+ tsoLatencyProcessor3);
			int totalTsoLatency = tsoLatencyProcessor0 + tsoLatencyProcessor1+ tsoLatencyProcessor2 + tsoLatencyProcessor3;
			System.out.println("TSO Total latency :: " + totalTsoLatency);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		int cacheLineCount, cacheLineSize, writeBufferSize, retireAtN;
		String traceFileName;

		Scanner in = new Scanner(System.in);

		System.out.print("Enter number of lines in the cache :: ");
		// cacheLineCount =in.nextInt();
		cacheLineCount = Integer.parseInt(args[0]); // 128

		System.out.print("Enter size of the cache line :: ");
		// cacheLineSize =in.nextInt();
		cacheLineSize = Integer.parseInt(args[1]); // 4

		System.out.print("Enter size of the write-buffer :: ");
		// cacheLineSize =in.nextInt();
		writeBufferSize = Integer.parseInt(args[2]); // 4

		System.out
				.print("Write Buffer Removal Policy ==> Retire-at-N (e.g.1,2 or 4) :: ");
		// cacheLineSize =in.nextInt();
		retireAtN = Integer.parseInt(args[3]); // 2

		// System.out.print("Enter name of the trace file (e.g trace1) :: ");
		// traceFileName =in.next();
		// traceFileName=args[2];

		HashMap<String, int[][]> hm = new HashMap<String, int[][]>();
		CacheCoherenceTSO cs = new CacheCoherenceTSO();
		hm = cs.initializeCache(cacheLineSize, cacheLineCount);

		HashMap<String, int[][]> hmWriteBuffer = new HashMap<String, int[][]>();
		hmWriteBuffer = cs.initializeWriteBuffer(writeBufferSize);
		// read trace file
		// String fileName=traceFileName;
		String fileName = "C:\\Users\\Shree\\junoworkspace\\CacheCoherence\\src\\testTrace1";
		// String
		// fileName="C:\\Users\\Shree\\junoworkspace\\CacheCoherence\\src\\trace1";
		cs.readTraceFile(cacheLineSize, cacheLineCount, hm, fileName,
				hmWriteBuffer, writeBufferSize, retireAtN);
		cs.printWriteBuffer(hmWriteBuffer);
	}

}
