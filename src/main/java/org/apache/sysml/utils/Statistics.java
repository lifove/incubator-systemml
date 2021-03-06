/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sysml.utils;

import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

import org.apache.sysml.api.DMLScript;
import org.apache.sysml.conf.ConfigurationManager;
import org.apache.sysml.conf.DMLConfig;
import org.apache.sysml.hops.OptimizerUtils;
import org.apache.sysml.runtime.controlprogram.caching.CacheStatistics;
import org.apache.sysml.runtime.controlprogram.context.SparkExecutionContext;
import org.apache.sysml.runtime.instructions.Instruction;
import org.apache.sysml.runtime.instructions.InstructionUtils;
import org.apache.sysml.runtime.instructions.MRJobInstruction;
import org.apache.sysml.runtime.instructions.cp.FunctionCallCPInstruction;
import org.apache.sysml.runtime.instructions.spark.SPInstruction;
import org.apache.sysml.runtime.matrix.data.LibMatrixDNN;

/**
 * This class captures all statistics.
 */
public class Statistics 
{
	private static long compileStartTime = 0;
	private static long compileEndTime = 0;
	
	private static long execStartTime = 0;
	private static long execEndTime = 0;

	// number of compiled/executed MR jobs
	private static final LongAdder numExecutedMRJobs = new LongAdder();
	private static final LongAdder numCompiledMRJobs = new LongAdder();

	// number of compiled/executed SP instructions
	private static final LongAdder numExecutedSPInst = new LongAdder();
	private static final LongAdder numCompiledSPInst = new LongAdder();

	//JVM stats (low frequency updates)
	private static long jitCompileTime = 0; //in milli sec
	private static long jvmGCTime = 0; //in milli sec
	private static long jvmGCCount = 0; //count
	
	//HOP DAG recompile stats (potentially high update frequency)
	private static final LongAdder hopRecompileTime = new LongAdder(); //in nano sec
	private static final LongAdder hopRecompilePred = new LongAdder(); //count
	private static final LongAdder hopRecompileSB = new LongAdder();   //count

	//CODEGEN
	private static final LongAdder codegenCompileTime = new LongAdder(); //in nano
	private static final LongAdder codegenClassCompileTime = new LongAdder(); //in nano
	private static final LongAdder codegenHopCompile = new LongAdder(); //count
	private static final LongAdder codegenCPlanCompile = new LongAdder(); //count
	private static final LongAdder codegenClassCompile = new LongAdder(); //count
	private static final LongAdder codegenPlanCacheHits = new LongAdder(); //count
	private static final LongAdder codegenPlanCacheTotal = new LongAdder(); //count
	
	//Function recompile stats 
	private static final LongAdder funRecompileTime = new LongAdder(); //in nano sec
	private static final LongAdder funRecompiles = new LongAdder(); //count
	
	//Spark-specific stats
	private static long sparkCtxCreateTime = 0; 
	private static final LongAdder sparkParallelize = new LongAdder();
	private static final LongAdder sparkParallelizeCount = new LongAdder();
	private static final LongAdder sparkCollect = new LongAdder();
	private static final LongAdder sparkCollectCount = new LongAdder();
	private static final LongAdder sparkBroadcast = new LongAdder();
	private static final LongAdder sparkBroadcastCount = new LongAdder();

	//PARFOR optimization stats (low frequency updates)
	private static long parforOptTime = 0; //in milli sec
	private static long parforOptCount = 0; //count
	private static long parforInitTime = 0; //in milli sec
	private static long parforMergeTime = 0; //in milli sec
	
	//heavy hitter counts and times 
	private static HashMap<String,Long> _cpInstTime = new HashMap<String, Long>();
	private static HashMap<String,Long> _cpInstCounts = new HashMap<String, Long>();

	private static final LongAdder lTotalUIPVar = new LongAdder();
	private static final LongAdder lTotalLix = new LongAdder();
	private static final LongAdder lTotalLixUIP = new LongAdder();

	public static synchronized long getNoOfExecutedMRJobs() {
		return numExecutedMRJobs.longValue();
	}
	
	public static void incrementNoOfExecutedMRJobs() {
		numExecutedMRJobs.increment();
	}
	
	public static void decrementNoOfExecutedMRJobs() {
		numExecutedMRJobs.decrement();
	}

	public static long getNoOfCompiledMRJobs() {
		return numCompiledMRJobs.longValue();
	}
	
	public static void incrementNoOfCompiledMRJobs() {
		numCompiledMRJobs.increment();
	}

	public static long getNoOfExecutedSPInst() {
		return numExecutedSPInst.longValue();
	}
	
	public static void incrementNoOfExecutedSPInst() {
		numExecutedSPInst.increment();
	}
	
	public static void decrementNoOfExecutedSPInst() {
		numExecutedSPInst.decrement();
	}

	public static long getNoOfCompiledSPInst() {
		return numCompiledSPInst.longValue();
	}

	public static void incrementNoOfCompiledSPInst() {
		numCompiledSPInst.increment();
	}
	
	public static long getTotalUIPVar() {
		return lTotalUIPVar.longValue();
	}

	public static void incrementTotalUIPVar() {
		lTotalUIPVar.increment();
	}

	public static long getTotalLixUIP() {
		return lTotalLixUIP.longValue();
	}

	public static void incrementTotalLixUIP() {
		lTotalLixUIP.increment();
	}

	public static long getTotalLix() {
		return lTotalLix.longValue();
	}

	public static void incrementTotalLix() {
		lTotalLix.increment();
	}

	public static void resetNoOfCompiledJobs( int count ) {
		//reset both mr/sp for multiple tests within one jvm
		numCompiledSPInst.reset();
		numCompiledMRJobs.reset();
		if( OptimizerUtils.isSparkExecutionMode() )
			numCompiledSPInst.add(count);
		else
			numCompiledMRJobs.add(count);
	}

	public static void resetNoOfExecutedJobs() {
		//reset both mr/sp for multiple tests within one jvm
		numExecutedSPInst.reset();
		numExecutedMRJobs.reset();
		
		if( DMLScript.USE_ACCELERATOR )
			GPUStatistics.setNoOfExecutedGPUInst(0);
	}
	
	public static synchronized void incrementJITCompileTime( long time ) {
		jitCompileTime += time;
	}
	
	public static synchronized void incrementJVMgcTime( long time ) {
		jvmGCTime += time;
	}
	
	public static synchronized void incrementJVMgcCount( long delta ) {
		jvmGCCount += delta;
	}
	
	public static void incrementHOPRecompileTime( long delta ) {
		hopRecompileTime.add(delta);
	}
	
	public static void incrementHOPRecompilePred() {
		hopRecompilePred.increment();
	}
	
	public static void incrementHOPRecompilePred(long delta) {
		hopRecompilePred.add(delta);
	}
	
	public static void incrementHOPRecompileSB() {
		hopRecompileSB.increment();
	}
	
	public static void incrementHOPRecompileSB(long delta) {
		hopRecompileSB.add(delta);
	}
	
	public static void incrementCodegenDAGCompile() {
		codegenHopCompile.increment();
	}
	
	public static void incrementCodegenCPlanCompile(long delta) {
		codegenCPlanCompile.add(delta);
	}
	
	public static void incrementCodegenClassCompile() {
		codegenClassCompile.increment();
	}
	
	public static void incrementCodegenCompileTime(long delta) {
		codegenCompileTime.add(delta);
	}
	
	public static void incrementCodegenClassCompileTime(long delta) {
		codegenClassCompileTime.add(delta);
	}
	
	public static void incrementCodegenPlanCacheHits() {
		codegenPlanCacheHits.increment();
	}
	
	public static void incrementCodegenPlanCacheTotal() {
		codegenPlanCacheTotal.increment();
	}
	
	public static long getCodegenDAGCompile() {
		return codegenHopCompile.longValue();
	}
	
	public static long getCodegenCPlanCompile() {
		return codegenCPlanCompile.longValue();
	}
	
	public static long getCodegenClassCompile() {
		return codegenClassCompile.longValue();
	}
	
	public static long getCodegenCompileTime() {
		return codegenCompileTime.longValue();
	}
	
	public static long getCodegenClassCompileTime() {
		return codegenClassCompileTime.longValue();
	}
	
	public static long getCodegenPlanCacheHits() {
		return codegenPlanCacheHits.longValue();
	}
	
	public static long getCodegenPlanCacheTotal() {
		return codegenPlanCacheTotal.longValue();
	}

	public static void incrementFunRecompileTime( long delta ) {
		funRecompileTime.add(delta);
	}
	
	public static void incrementFunRecompiles() {
		funRecompiles.increment();
	}
	
	public static synchronized void incrementParForOptimCount(){
		parforOptCount ++;
	}
	
	public static synchronized void incrementParForOptimTime( long time ) {
		parforOptTime += time;
	}
	
	public static synchronized void incrementParForInitTime( long time ) {
		parforInitTime += time;
	}
	
	public static synchronized void incrementParForMergeTime( long time ) {
		parforMergeTime += time;
	}

	public static void startCompileTimer() {
		if( DMLScript.STATISTICS )
			compileStartTime = System.nanoTime();
	}

	public static void stopCompileTimer() {
		if( DMLScript.STATISTICS )
			compileEndTime = System.nanoTime();
	}

	public static long getCompileTime() {
		return compileEndTime - compileStartTime;
	}
	
	/**
	 * Starts the timer, should be invoked immediately before invoking
	 * Program.execute()
	 */
	public static void startRunTimer() {
		execStartTime = System.nanoTime();
	}

	/**
	 * Stops the timer, should be invoked immediately after invoking
	 * Program.execute()
	 */
	public static void stopRunTimer() {
		execEndTime = System.nanoTime();
	}

	/**
	 * Returns the total time of run in nanoseconds.
	 * 
	 * @return run time in nanoseconds
	 */
	public static long getRunTime() {
		return execEndTime - execStartTime;
	}
	
	public static void reset()
	{
		hopRecompileTime.reset();
		hopRecompilePred.reset();
		hopRecompileSB.reset();
		
		funRecompiles.reset();
		funRecompileTime.reset();
		
		parforOptCount = 0;
		parforOptTime = 0;
		parforInitTime = 0;
		parforMergeTime = 0;
		
		lTotalLix.reset();
		lTotalLixUIP.reset();
		lTotalUIPVar.reset();
		
		resetJITCompileTime();
		resetJVMgcTime();
		resetJVMgcCount();
		resetCPHeavyHitters();

		GPUStatistics.reset();
		LibMatrixDNN.resetStatistics();
	}

	public static void resetJITCompileTime(){
		jitCompileTime = -1 * getJITCompileTime();
	}
	
	public static void resetJVMgcTime(){
		jvmGCTime = -1 * getJVMgcTime();
	}
	
	public static void resetJVMgcCount(){
		jvmGCTime = -1 * getJVMgcCount();
	}

	public static void resetCPHeavyHitters(){
		_cpInstTime.clear();
		_cpInstCounts.clear();
	}

	public static void setSparkCtxCreateTime(long ns) {
		sparkCtxCreateTime = ns;
	}
	
	public static void accSparkParallelizeTime(long t) {
		sparkParallelize.add(t);
	}

	public static void incSparkParallelizeCount(long c) {
		sparkParallelizeCount.add(c);
	}

	public static void accSparkCollectTime(long t) {
		sparkCollect.add(t);
	}

	public static void incSparkCollectCount(long c) {
		sparkCollectCount.add(c);
	}

	public static void accSparkBroadCastTime(long t) {
		sparkBroadcast.add(t);
	}

	public static void incSparkBroadcastCount(long c) {
		sparkBroadcastCount.add(c);
	}
	
	
	public static String getCPHeavyHitterCode( Instruction inst )
	{
		String opcode = null;
		
		if( inst instanceof MRJobInstruction )
		{
			MRJobInstruction mrinst = (MRJobInstruction) inst;
			opcode = "MR-Job_"+mrinst.getJobType();
		}
		else if( inst instanceof SPInstruction )
		{
			opcode = "SP_"+InstructionUtils.getOpCode(inst.toString());
			if( inst instanceof FunctionCallCPInstruction ) {
				FunctionCallCPInstruction extfunct = (FunctionCallCPInstruction)inst;
				opcode = extfunct.getFunctionName();
			}	
		}
		else //CPInstructions
		{
			opcode = InstructionUtils.getOpCode(inst.toString());
			if( inst instanceof FunctionCallCPInstruction ) {
				FunctionCallCPInstruction extfunct = (FunctionCallCPInstruction)inst;
				opcode = extfunct.getFunctionName();
			}		
		}
		
		return opcode;
	}

	/**
	 * "Maintains" or adds time to per instruction/op timers, also increments associated count
	 * @param instructionName name of the instruction/op
	 * @param timeNanos time in nano seconds
	 */
	public synchronized static void maintainCPHeavyHitters( String instructionName, long timeNanos )
	{
		Long oldVal = _cpInstTime.getOrDefault(instructionName, 0L);
		_cpInstTime.put(instructionName, oldVal + timeNanos);

		Long oldCnt = _cpInstCounts.getOrDefault(instructionName, 0L);
		_cpInstCounts.put(instructionName, oldCnt + 1);
	}


	public static Set<String> getCPHeavyHitterOpCodes() {
		return _cpInstTime.keySet();
	}
	
	public static long getCPHeavyHitterCount(String opcode) {
		return _cpInstCounts.get(opcode);
	}

	@SuppressWarnings("unchecked")
	public static String getHeavyHitters( int num )
	{
		int len = _cpInstTime.size();
		if( num <= 0 || len <= 0 )
			return "-";
		
		//get top k via sort
		Entry<String,Long>[] tmp = _cpInstTime.entrySet().toArray(new Entry[len]);
		Arrays.sort(tmp, new Comparator<Entry<String, Long>>() {
		    public int compare(Entry<String, Long> e1, Entry<String, Long> e2) {
		        return e1.getValue().compareTo(e2.getValue());
		    }
		});
		
		//prepare output string
		StringBuilder sb = new StringBuilder();
		for( int i=0; i<Math.min(num, len); i++ ){
			String key = tmp[len-1-i].getKey();
			sb.append("-- "+(i+1)+") \t");
			sb.append(key);
			sb.append(" \t");
			sb.append(String.format("%.3f", ((double)tmp[len-1-i].getValue())/1000000000));
			sb.append(" sec \t");
			sb.append(_cpInstCounts.get(key));
			sb.append("\t");
			// Add the miscellaneous timer info
			if (GPUStatistics.DISPLAY_STATISTICS) {
				sb.append(GPUStatistics.getStringForCPMiscTimesPerInstruction(key));
			}
			sb.append("\n");
		}
		
		return sb.toString();
	}
	
	/**
	 * Returns the total time of asynchronous JIT compilation in milliseconds.
	 * 
	 * @return JIT compile time
	 */
	public static long getJITCompileTime(){
		long ret = -1; //unsupported
		CompilationMXBean cmx = ManagementFactory.getCompilationMXBean();
		if( cmx.isCompilationTimeMonitoringSupported() )
		{
			ret = cmx.getTotalCompilationTime();
			ret += jitCompileTime; //add from remote processes
		}
		return ret;
	}
	
	public static long getJVMgcTime(){
		long ret = 0; 
		
		List<GarbageCollectorMXBean> gcxs = ManagementFactory.getGarbageCollectorMXBeans();
		
		for( GarbageCollectorMXBean gcx : gcxs )
			ret += gcx.getCollectionTime();
		if( ret>0 )
			ret += jvmGCTime;
		
		return ret;
	}
	
	public static long getJVMgcCount(){
		long ret = 0; 
		
		List<GarbageCollectorMXBean> gcxs = ManagementFactory.getGarbageCollectorMXBeans();
		
		for( GarbageCollectorMXBean gcx : gcxs )
			ret += gcx.getCollectionCount();
		if( ret>0 )
			ret += jvmGCCount;
		
		return ret;
	}
	
	public static long getHopRecompileTime(){
		return hopRecompileTime.longValue();
	}
	
	public static long getHopRecompiledPredDAGs(){
		return hopRecompilePred.longValue();
	}
	
	public static long getHopRecompiledSBDAGs(){
		return hopRecompileSB.longValue();
	}
	
	public static long getFunRecompileTime(){
		return funRecompileTime.longValue();
	}
	
	public static long getFunRecompiles(){
		return funRecompiles.longValue();
	}
		
	public static long getParforOptCount(){
		return parforOptCount;
	}
	
	public static long getParforOptTime(){
		return parforOptTime;
	}
	
	public static long getParforInitTime(){
		return parforInitTime;
	}
	
	public static long getParforMergeTime(){
		return parforMergeTime;
	}

	/**
	 * Returns statistics of the DML program that was recently completed as a string
	 * @return statistics as a string
	 */
	public static String display() {
		return display(DMLScript.STATISTICS_COUNT);
	}

	/**
	 * Returns statistics as a string
	 * @param maxHeavyHitters The maximum number of heavy hitters that are printed
	 * @return statistics as string
	 */
	public static String display(int maxHeavyHitters)
	{
		StringBuilder sb = new StringBuilder();
		
		sb.append("SystemML Statistics:\n");
		if( DMLScript.STATISTICS ) {
			sb.append("Total elapsed time:\t\t" + String.format("%.3f", (getCompileTime()+getRunTime())*1e-9) + " sec.\n"); // nanoSec --> sec
			sb.append("Total compilation time:\t\t" + String.format("%.3f", getCompileTime()*1e-9) + " sec.\n"); // nanoSec --> sec
		}
		sb.append("Total execution time:\t\t" + String.format("%.3f", getRunTime()*1e-9) + " sec.\n"); // nanoSec --> sec
		if( OptimizerUtils.isSparkExecutionMode() ) {
			if( DMLScript.STATISTICS ) //moved into stats on Shiv's request
				sb.append("Number of compiled Spark inst:\t" + getNoOfCompiledSPInst() + ".\n");
			sb.append("Number of executed Spark inst:\t" + getNoOfExecutedSPInst() + ".\n");
		}
		else {
			if( DMLScript.STATISTICS ) //moved into stats on Shiv's request
				sb.append("Number of compiled MR Jobs:\t" + getNoOfCompiledMRJobs() + ".\n");
			sb.append("Number of executed MR Jobs:\t" + getNoOfExecutedMRJobs() + ".\n");	
		}

		if( DMLScript.USE_ACCELERATOR && DMLScript.STATISTICS)
			sb.append(GPUStatistics.getStringForCudaTimers());
		
		//show extended caching/compilation statistics
		if( DMLScript.STATISTICS ) 
		{
			sb.append("Cache hits (Mem, WB, FS, HDFS):\t" + CacheStatistics.displayHits() + ".\n");
			sb.append("Cache writes (WB, FS, HDFS):\t" + CacheStatistics.displayWrites() + ".\n");
			sb.append("Cache times (ACQr/m, RLS, EXP):\t" + CacheStatistics.displayTime() + " sec.\n");
			sb.append("HOP DAGs recompiled (PRED, SB):\t" + getHopRecompiledPredDAGs() + "/" + getHopRecompiledSBDAGs() + ".\n");
			sb.append("HOP DAGs recompile time:\t" + String.format("%.3f", ((double)getHopRecompileTime())/1000000000) + " sec.\n");
			if( getFunRecompiles()>0 ) {
				sb.append("Functions recompiled:\t\t" + getFunRecompiles() + ".\n");
				sb.append("Functions recompile time:\t" + String.format("%.3f", ((double)getFunRecompileTime())/1000000000) + " sec.\n");	
			}
			if( ConfigurationManager.getDMLConfig().getBooleanValue(DMLConfig.CODEGEN) ) {
				sb.append("Codegen compile (DAG, CP, JC):\t" + getCodegenDAGCompile() + "/" + getCodegenCPlanCompile() + "/" + getCodegenClassCompile() + ".\n");
				sb.append("Codegen compile times (DAG,JC):\t" + String.format("%.3f", (double)getCodegenCompileTime()/1000000000) + "/" + 
						String.format("%.3f", (double)getCodegenClassCompileTime()/1000000000)  + " sec.\n");
				sb.append("Codegen plan cache hits:\t" + getCodegenPlanCacheHits() + "/" + getCodegenPlanCacheTotal() + ".\n");
			}
			if( OptimizerUtils.isSparkExecutionMode() ){
				String lazy = SparkExecutionContext.isLazySparkContextCreation() ? "(lazy)" : "(eager)";
				sb.append("Spark ctx create time "+lazy+":\t"+
						String.format("%.3f", ((double)sparkCtxCreateTime)*1e-9)  + " sec.\n" ); // nanoSec --> sec
				sb.append("Spark trans counts (par,bc,col):" +
						String.format("%d/%d/%d.\n", sparkParallelizeCount.longValue(), 
								sparkBroadcastCount.longValue(), sparkCollectCount.longValue()));
				sb.append("Spark trans times (par,bc,col):\t" +
						String.format("%.3f/%.3f/%.3f secs.\n", 
								 ((double)sparkParallelize.longValue())*1e-9,
								 ((double)sparkBroadcast.longValue())*1e-9,
								 ((double)sparkCollect.longValue())*1e-9));
			}
			if( parforOptCount>0 ){
				sb.append("ParFor loops optimized:\t\t" + getParforOptCount() + ".\n");
				sb.append("ParFor optimize time:\t\t" + String.format("%.3f", ((double)getParforOptTime())/1000) + " sec.\n");	
				sb.append("ParFor initialize time:\t\t" + String.format("%.3f", ((double)getParforInitTime())/1000) + " sec.\n");	
				sb.append("ParFor result merge time:\t" + String.format("%.3f", ((double)getParforMergeTime())/1000) + " sec.\n");	
				sb.append("ParFor total update in-place:\t" + lTotalUIPVar + "/" + lTotalLixUIP + "/" + lTotalLix + "\n");
			}

			sb.append("Total JIT compile time:\t\t" + ((double)getJITCompileTime())/1000 + " sec.\n");
			sb.append("Total JVM GC count:\t\t" + getJVMgcCount() + ".\n");
			sb.append("Total JVM GC time:\t\t" + ((double)getJVMgcTime())/1000 + " sec.\n");
			LibMatrixDNN.appendStatistics(sb);
			sb.append("Heavy hitter instructions (name, time, count):\n" + getHeavyHitters(maxHeavyHitters));
		}
		
		return sb.toString();
	}
}
