/*
 * Copyright (c) 2018, Design Engineering Laboratory, The University of Tokyo.
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the project nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE PROJECT AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE PROJECT OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package org.pdes.simulator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.pdes.simulator.base.PDES_AbstractSimulator;
import org.pdes.simulator.model.Component;
import org.pdes.simulator.model.Organization;
import org.pdes.simulator.model.Request;
import org.pdes.simulator.model.Task;
import org.pdes.simulator.model.Workflow;
import org.pdes.simulator.model.Worker;
import org.pdes.simulator.model.base.BaseFacility;
import org.pdes.simulator.model.base.BaseProjectInfo;
import org.pdes.simulator.model.base.BaseTask;
import org.pdes.simulator.model.base.BaseTeam;
import org.pdes.simulator.model.base.BaseWorker;

/**
 * Simulation to design "Broker Function Position" in Organization.
 * 
 * @author Yoshiaki Oida<yoida@s.h.k.u-tokyo.ac.jp>
 *
 */
public class PDES_OidaSimulator extends PDES_AbstractSimulator{
	
	//Additional
	static public final int maxTime = 100;	
	static public List<Worker> allWorkerList;
	static public ArrayList<Component> projectList;
	static public int brokerId = 5;//Change
	public List<Request> releaseList;//release
	public List<Request> supplyRequestList; //supply request
	//public List<Request> confirmList;//supply request
	public List<Request> finalConfirmList;//supply request
	
	public PDES_OidaSimulator(BaseProjectInfo project) {
		super(project);
		releaseList = new ArrayList<>();
		supplyRequestList = new ArrayList<>();
		//confirmList = new ArrayList<>();
		finalConfirmList = new ArrayList<>();
	}
	/**
	 * TODO
	 * -CaseStudy
	 *  - Modeling
	 *  - Simulation 
	 *  
	 * -Initial Allocation
	 *  - Allocation based on the estimated Work Amount.
	 *  
	 * -How to Repeat Simulation based on Imported pdm.
	 *  - pdm -> 繰り返し
	 *  
	 * -Better
	 *  - IMPLEMENT BR -> WR delay
	 */

	@Override
	public void execute() {
		this.initialize();
		
		/**
		 * Project Portfolio Initialize
		 */

		//All Projects
		projectList =this.productList.stream()
				.flatMap(p -> p.getComponentList().stream())
				.collect(
						() -> new ArrayList<>(),
						(l,c) -> l.add((Component)c),
						(l1,l2) -> l1.addAll(l2)
						);
				
		//All Workers
		allWorkerList = this.organization.getWorkerList().stream()
				.map(w -> (Worker)w)
				.collect(Collectors.toList());
				
		System.out.println();
		System.out.println("*** Project Portfolio Information ***");
		System.out.println("Number of project : " + projectList.size());
		System.out.println("Project portfolio : " + projectList);
		projectList.stream().forEach(c -> System.out.println(c.getName() + " : " + c.getTargetedTaskList()));
		
		
		/**
		 * 1.Initial Allocation
		 * a.Simulate under all same conditions except the following. 
		 * 	 - NOT UPDATE Remaining Work Amount BY Actual Cost
		 * b.Fixed Workers for each Project on the basis of skill set.
		 *   - ex. 1 Worker for project 1, 2 workers for project2. 
		 *  b(easy) is first.
		 */
		System.out.println();
		System.out.println("*** Initial Allocation ***");
				
		//Just confirm worker's Executable Task
		allWorkerList.stream().forEach(w -> 
			projectList.stream().forEach(c -> 
				System.out.println(w.getName() +" : "+c.getName()+" "+w.getExecutableUnfinishedTaskList(c))
				)
			);
		
		//Initial Allocation (Assign Worker to Max Skill-Task Matching Project)
		allWorkerList.stream().forEach(w -> 
			w.setCurrentAssignedProject(
					projectList.stream()
					.max((c1,c2) -> w.getExecutableUnfinishedTaskList(c1).size()-w.getExecutableUnfinishedTaskList(c2).size())
					.orElse(null) //null : ResourcePool
					)
			);
		
		//Update Assigned Project Plan
		allWorkerList.stream().forEach(w -> 
			w.setAssignedProjectPlanArray(
					-1, //default setting time
					0, //start
					w.getCurrentAssignedProject().getDueDate()+1, //end (this value is excluded.)
					projectList.indexOf(w.getCurrentAssignedProject()) //projectIndex
				)
			);

		//(vice-versa) Add assignment information in Project 
		projectList.stream().forEach(c ->
				c.setWorkerList(
						allWorkerList.stream()
						.filter(w -> c.equals(w.getCurrentAssignedProject()))
						.collect(Collectors.toList())
						)
				);
		
		//Initial Estimation for Initial Allocation
		for(	Component c : projectList) {
			//Estimate Total Work Amount, Completion Time, Required Resources
			c.estimeate(time);
			System.out.println(c.toString());
		}
		
		//Loop max time 
		while(true){
			if(time >= PDES_OidaSimulator.maxTime) {
				System.out.println();
				System.out.println("t: "+time+" *** Time Over *** Time "+time+" is larger than max time"+ PDES_OidaSimulator.maxTime+".");
				break;
			}

			//0. Check finished or not.
			if(checkAllTasksAreFinished()) {
				System.out.println();
				System.out.println("t: "+time+" *** All Done *** No More Projects and Tasks. All Projects and Tasks Finihsed!");
				return;
			}

			//A. Confirm Assigned Project Members on the basis of Assigned Project Plan
			System.out.println();
			System.out.println("t: " +time+ " *** Update Current Assigned Project ***");

			//Update Worker Assigned Project based on Plan
			allWorkerList.stream().forEach(w -> 
			w.setCurrentAssignedProject(w.getLatestAssignedProjectPlanArray()[time] != -1 
			? projectList.get(w.getLatestAssignedProjectPlanArray()[time]) : null)
					);

			//(vice-versa) Update Project(Component) Worker Assignment  based on Plan
			projectList.stream().forEach(c ->
			c.setWorkerList(
					allWorkerList.stream()
					.filter(w -> c.equals(w.getCurrentAssignedProject()))
					.collect(Collectors.toList())
					)
					);

			//Just confirm worker's Assignment 
			allWorkerList.stream().forEach(w -> 
			System.out.println(w.getName() +" : "+(w.getCurrentAssignedProject()!=null ? w.getCurrentAssignedProject().getName():"Resource Pool"))
					);

			//Update Worker Assignment History
			allWorkerList.stream().forEach(w -> 
			w.getAssignedProjectHistoryArray()[time] = w.getLatestAssignedProjectPlanArray()[time]
					);	

			//B. Project Execution
			System.out.println();
			System.out.println("t: " +time+ " *** Project Execution ***");

			for(	Component c : projectList) {
				//1. Get ready task and free resources for each project.
				List<BaseTask> readyTaskList = this.getReadyTaskList(c);
				List<BaseWorker> freeWorkerList = ((Organization)organization).getFreeWorkerList(c);
				List<BaseFacility> freeFacilityList = organization.getFreeFacilityList();//ignore

				/**
				 * Don't want change the following part if possible.
				 */
				//2. Sort ready task and free resources
				this.sortTasks(readyTaskList);
				this.sortWorkers(freeWorkerList);
				this.sortFacilities(freeFacilityList);

				//3. Allocate ready tasks to free resources
				this.allocateReadyTasksToFreeResourcesForSingleTaskWorkerSimulation(readyTaskList, freeWorkerList, freeFacilityList);

				//4. Perform WORKING tasks and update the status of each task.
				this.performAndUpdateAllWorkflow(time, c);

				//Just Show finished Task List
				System.out.println(c.getName() + "(Finished) : " +c.getTargetedTaskList().stream()
						.filter(t -> t.isFinished())
						.map(t -> (Task)t)
						.collect(Collectors.toList()));
			}

			//C. Re-Allocation
			System.out.println();
			System.out.println("t: " +time+ " *** Resource Re-allocation ***");

			//Show Communication Matrix
			Request.showCommunicationMatrix();

			for(	Component c : projectList) {
				/**
				 * Release all resources when a project finishes,
				 */
				//Project index
				int projectIndex = projectList.indexOf(c);

				//Just Show Unfinished Task List
				System.out.println(c.getName() + "(Unfinished) : " + c.getUnfinishedTaskList());

				//Check project finish or not.
				if(c.getUnfinishedTaskList().size() == 0 && c.getFinishTime() == -1) {
					//Project Finished.
					c.setFinishTime(time);
					for (Worker w : c.getWorkerList()) {
						boolean updateFlag = false;
						for(int t = time+1; t < PDES_OidaSimulator.maxTime; t++) {
							if(w.getLatestAssignedProjectPlanArray()[t] == projectIndex) {
								w.getLatestAssignedProjectPlanArray()[t] = -1; //Release
								updateFlag = true;
							}
						}
						//Update AssignedProjectPlanArray
						if(updateFlag) w.setAssignedProjectPlanArray(time, w.getLatestAssignedProjectPlanArray());
					}
				}
				/**
				 * Release and Supply Request based on Estimation by Project Manager.
				 */
				//(PM) Estimate Total Remaining Work Amount, Completion Time, Required Resources
				c.estimeate(time);
				System.out.println(c.toString());

				//Just Show Communication Matrix
				Request.showCommunicationMatrix();

				//(PM) 1.Release, 2. Supply Request or 3. nothing 
				if(c.getEstimatedDelay() < 0) {
					//New Release *Release {ct' < dd} <-> {time + WL'/|RA| < dd} -> {time < dd}    *WL'/|RA| > 0
					releaseList.addAll(c.sendRelease(time));
				}else if(c.getEstimatedDelay() > 0){
					//New Supply Request *Supply Request ct' > dd <-> time + WL'/|RA| > dd -> (time < dd) or (time > dd)
					supplyRequestList.addAll(c.sendSupplyRequest(time));
				}else {//Do nothing!
				}
			}

			/**
			 * When Request Arrives, Do Action.
			 */
			//(WR) Receive Release. 
			releaseList.stream()
			.filter(r -> r.checkArrival(time))
			.forEach(r -> ((Worker)Request.getObject(r.getToIndex())).setAssignedProjectPlanArray(time, r.getTargetTimeSlotArray()));

			//(BR) Receive Supply Request.
			for(Request r : supplyRequestList) {
				if(!r.checkArrival(time))continue;

				double estimatedLackOfWorkAmount = r.getWorkAmount();

				//Request from Who
				Component c = (Component)Request.getObject(r.getFromIndex());
				//Project index
				int projectIndex = projectList.indexOf(c);

				//-----
				//Now Confirmation has no delay.TODO add delay.
				//Priority of Workers to be supplied. Which worker should be supplied? "Maximum" Matching Skill.
				List<Worker> workerListToBeSupplied = allWorkerList.stream()
						.filter(w -> w.getExecutableUnfinishedTaskList(c).size() > 0) //filter workers who has no-skill for unfinished tasks.
						.sorted((w1,w2) -> w2.getExecutableUnfinishedTaskList(c).size() - w1.getExecutableUnfinishedTaskList(c).size())// Todo:How About Skill Point?
						.collect(Collectors.toList());
				//-----

				//Declare
				HashMap<Worker, Boolean> updateFlags = new HashMap<Worker,Boolean>();
				HashMap<Worker, Integer[]> targetTimeSlotArrays = new HashMap<Worker, Integer[]> ();
				double workAmountToBeSupplied = 0;

				//Initialize
				workerListToBeSupplied.stream().forEach(w -> updateFlags.put(w, false));
				allWorkerList.stream().forEach(w -> {
					Integer[] targetTimeSlotArray = new Integer[PDES_OidaSimulator.maxTime];
					System.arraycopy(w.getLatestAssignedProjectPlanArray(), 0,
							targetTimeSlotArray, 0, w.getLatestAssignedProjectPlanArray().length);
					targetTimeSlotArrays.put(w, targetTimeSlotArray );
				});

				if(time <= c.getDueDate()) {//time <= due date						
					//[t+1,dd] Select time slots to be supplied. Priority : 1.Worker -> 2.Time(Forward)
					for (Worker w : workerListToBeSupplied) {
						for(int t = time+1; t < c.getDueDate()+1; t++) {
							if(estimatedLackOfWorkAmount <= workAmountToBeSupplied) break;
							if(w.getLatestAssignedProjectPlanArray()[t] == -1) {
								targetTimeSlotArrays.get(w)[t] = projectIndex; //Supplied
								workAmountToBeSupplied += 1;
								updateFlags.put(w, true);
							}
						}
					}
					//(dd, t) Select time slots to be supplied. Priority : 1.Time(Forward)　-> 2.Worker 
					for(int t =  c.getDueDate()+1; t < Math.min(c.getEstimatedCompletionTime(),PDES_OidaSimulator.maxTime); t++) {
						for (Worker w : workerListToBeSupplied) {
							if(estimatedLackOfWorkAmount <= workAmountToBeSupplied) break;
							if(w.getLatestAssignedProjectPlanArray()[t] == -1) {
								targetTimeSlotArrays.get(w)[t] = projectIndex; //Supplied
								workAmountToBeSupplied += 1;
								updateFlags.put(w, true);
							}
						}
					}
					//Add confirm list
					workerListToBeSupplied.stream()
					.filter(w -> updateFlags.get(w))
					.forEach(w -> finalConfirmList.add(new Request(time, PDES_OidaSimulator.brokerId, Request.indexOf(w),targetTimeSlotArrays.get(w))));

				}else {//time > due date
					//Select time slots to be supplied. Priority : 1.Time(Forward) -> 2.Worker
					for(int t = time+1; t < Math.min(c.getEstimatedCompletionTime(),PDES_OidaSimulator.maxTime); t++) {
						for (Worker w : workerListToBeSupplied) {
							if(estimatedLackOfWorkAmount <= workAmountToBeSupplied) break;
							if(w.getLatestAssignedProjectPlanArray()[t] == -1) {
								targetTimeSlotArrays.get(w)[t] = projectIndex; //Supplied
								workAmountToBeSupplied += 1;
								updateFlags.put(w, true);
							}
						}
					}
					//Add confirm list
					workerListToBeSupplied.stream()
					.filter(w -> updateFlags.get(w))
					.forEach(w -> finalConfirmList.add(new Request(time, PDES_OidaSimulator.brokerId, Request.indexOf(w), targetTimeSlotArrays.get(w))));
				}	
			}

			//(WR) Receive Re-Assignment.
			finalConfirmList.stream()
			.filter(r -> r.checkArrival(time))
			.forEach(r -> ((Worker)Request.getObject(r.getToIndex())).setAssignedProjectPlanArray(time, r.getTargetTimeSlotArray()));

			/**
			 * update request status
			 */
			releaseList.stream().forEach(r -> r.updateRemainlingTime());
			supplyRequestList.stream().forEach(r -> r.updateRemainlingTime());
			//confirmList.stream().forEach(r -> r.updateRemainlingTime());
			finalConfirmList.stream().forEach(r -> r.updateRemainlingTime());

			time++;
		}
	}
	
	/**
	 * Get the list of tasks.
	 * @return
	 */
	public List<BaseTask> getTaskList(Component c){
		return super.workflowList.stream()
				.map(w -> ((Workflow)w).getTaskList(c))
				.collect(
						() -> new ArrayList<>(),
						(l, t) -> l.addAll(t),
						(l1, l2) -> l1.addAll(l2)
						);
	}

	/**
	 * Get the list of READY tasks.
	 * @return
	 */
	public List<BaseTask> getReadyTaskList(Component c){
		return super.workflowList.stream()
				.map(w -> ((Workflow)w).getReadyTaskList(c))
				.collect(
						() -> new ArrayList<>(),
						(l, t) -> l.addAll(t),
						(l1, l2) -> l1.addAll(l2)
						);
	}
	
	/**
	 * Perform and update project c workflow in this time.
	 * @param time 
	 */
	public void performAndUpdateAllWorkflow(int time, Component c){
		workflowList.forEach(w -> ((Workflow)w).checkWorking(time, c));//READY -> WORKING
		
		((Organization)organization).getWorkingWorkerList(c).stream().forEach(w -> w.addLaborCost());//pay labor cost
		//Update Task History Array
		workflowList.forEach(wf -> {
			((Organization)organization).getWorkingWorkerList().stream()
				.forEach(wr -> {
					((Worker)wr).getAssignedTaskHistoryArray()[time] = 
							((Workflow)wf).getTaskList()
							.indexOf(((Worker)wr).getAssignedTaskList().get(((Worker)wr).getAssignedTaskList().size()-1));
			});});

		//((Organization)organization).getWorkingFacilityList(c).stream().forEach(f -> f.addLaborCost());//pay labor cost //ignore
		workflowList.forEach(w -> ((Workflow)w).perform(time, c));//update information of WORKING task in each workflow
		workflowList.forEach(w -> ((Workflow)w).checkFinished(time, c));// WORKING -> WORKING_ADDITIONALLY or FINISHED
		workflowList.forEach(w -> ((Workflow)w).checkReady(time, c));// NONE -> READY
		workflowList.forEach(w -> ((Workflow)w).updatePERTData(time,c));//Update PERT information
	}

	public List<Worker> getAllWorkerList() {
		return allWorkerList;
	}
	
	public List<Component> getProjectList() {
		return projectList;
	}
		
	/**
	 * Save result file including oida visualization by csv format.
	 * @param outputDirName
	 * @param resultFileName
	 */
	@Override
	public void saveResultFileByCsv(String outputDirName, String resultFileName){
	//TO IMPLEMENT Average Project Delay -> CSV Output

				
		File resultFile = new File(outputDirName, resultFileName);
		String separator = ",";
		try {
			// BOM
			FileOutputStream os = new FileOutputStream(resultFile);
			os.write(0xef);
			os.write(0xbb);
			os.write(0xbf);
			
			PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os)));
			
			//Projects
			ArrayList<Component> projectList =this.productList.stream()
					.flatMap(p -> p.getComponentList().stream())
					.collect(
							() -> new ArrayList<>(),
							(l,c) -> l.add((Component)c),
							(l1,l2) -> l1.addAll(l2)
							);
			//summary
			pw.print("Summary"+separator+"Portfolio"+separator);
			projectList.stream().forEach(c -> pw.print(c.getName()+separator));
			pw.println();
			
			pw.print("Total Work Amount"+separator+String.valueOf(project.getTotalActualWorkAmount())+separator);
			projectList.stream().forEach(c -> pw.print(c.getActualTotalWorkAmount()+separator));
			pw.println();
			
			pw.print("Duration"+separator+String.valueOf(project.getDuration()+1)+separator);
			projectList.stream().forEach(c -> pw.print((c.getFinishTime()-c.getStartTime()+1)+separator));
			pw.println();
			
			pw.print("Average Project Delay"+separator+ String.valueOf(projectList.stream().mapToDouble(c -> Math.max(0, c.getFinishTime()-c.getDueDate())).average().orElse(-1))+separator);
			projectList.stream().forEach(c -> pw.print(Math.max(0, c.getFinishTime()-c.getDueDate())+separator));
			pw.println();
			
			pw.println("Total Cost"+separator+ String.valueOf(project.getTotalCost())+separator);
			pw.println();
			
			//time
			pw.println(separator +"time");
			pw.print(" "+separator);//dummy
			for (int time = 0; time < PDES_OidaSimulator.maxTime; time++) {
				pw.print(time+separator);
			}
			pw.println();
			
			pw.println("AssignedProjectHistoryArray");
			this.getAllWorkerList().stream().forEach(w -> {
				pw.print(w.getName()+separator);
				for (int time = 0; time < PDES_OidaSimulator.maxTime; time++) {
					pw.print(w.getAssignedProjectHistoryArray()[time]+separator);
				}
				pw.println();
			});
			pw.println("AssignedTaskHistoryArray");
			this.getAllWorkerList().stream().forEach(w -> {
				pw.print(w.getName()+separator);
				for (int time = 0; time < PDES_OidaSimulator.maxTime; time++) {
					pw.print(w.getAssignedTaskHistoryArray()[time]+separator);
				}
				pw.println();
			});
			
			pw.println("AssignedProjectPlanArrayList");
			this.getAllWorkerList().stream().forEach(w -> {
				pw.println(w.getName()+":");
				for (Integer[] assignedProjectPlanArray : w.getAssignedProjectPlanArrayList()) {
					for (int time = 0; time < PDES_OidaSimulator.maxTime + 1; time++) {
						pw.print(assignedProjectPlanArray[time]+separator);//at time, time(0~maxtime) 
					}
					pw.println();
				}
			});
			pw.println();
			
			pw.println("ReleaseList");
			
			for (int i = 0; i < this.getProjectList().size(); i++) {
				pw.print(this.getProjectList().get(i).getName()+separator);
				for(Request r : this.releaseList)
					if(r.getFromIndex() == Request.indexOf(projectList.get(i))) {
						pw.print(String.format("[DT]%03d [AT]%03d [W]%s,",
								r.getDepartureTime(),
								r.getArrivalTime(),
								((Worker)Request.getObject(r.getToIndex())).getName()
								));
					}
				pw.println();
			}

			pw.println("SupplyRequestList");
			
			for (int i = 0; i < this.getProjectList().size(); i++) {
				pw.print(this.getProjectList().get(i).getName()+separator);
				for(Request r : this.supplyRequestList)
					if(r.getFromIndex() == Request.indexOf(projectList.get(i))) {
						pw.print(String.format("[DT]%03d [AT]%03d [B]%d,",
								r.getDepartureTime(),
								r.getArrivalTime(),
								PDES_OidaSimulator.brokerId
								));
					}
				pw.println();
			}

			pw.println("FinalConfirmList");
			pw.print("FinalConfirmList"+separator);
			for(Request r : this.finalConfirmList)
			{
				pw.print(String.format("[DT]%03d [AT]%03d [B]%d [W]%s,",
						r.getDepartureTime(),
						r.getArrivalTime(),
						PDES_OidaSimulator.brokerId,
						((Worker)Request.getObject(r.getToIndex())).getName()
						));
			}
			pw.println();

			// header
			pw.println(String.join(separator, new String[]{"Total Cost", String.valueOf(project.getTotalCost()), "Duration", String.valueOf(project.getDuration()+1), "Total Work Amount", String.valueOf(project.getTotalActualWorkAmount())}));
			
			// workflow
			pw.println();
			pw.println("Gantt chart of each Task");
			pw.println(String.join(separator , new String[]{"Workflow", "Task", "Assigned Team", "Ready Time", "Start Time", "Finish Time", "Start Time", "Finish Time", "Start Time", "Finish Time"}));
			this.workflowList.forEach(w -> {
				String workflowName = "Workflow ("+w.getDueDate()+")";
				w.getTaskList().forEach(t ->{
					List<String> baseInfo = new ArrayList<String>();
					baseInfo.add(workflowName);
					baseInfo.add(t.getName());
					//baseInfo.add(t.getAllocatedTeam().getName());
					baseInfo.add(t.getAllocatedTeamList().stream().map(BaseTeam::getName).collect(Collectors.joining("+")));
					IntStream.range(0, t.getFinishTimeList().size()).forEach(i -> {
						baseInfo.add(String.valueOf(t.getReadyTimeList().get(i)));
						baseInfo.add(String.valueOf(t.getStartTimeList().get(i)));
						baseInfo.add(String.valueOf(t.getFinishTimeList().get(i)));
					});
					pw.println(String.join(separator ,baseInfo.stream().toArray(String[]::new)));
				});
			});
			
			// product
			pw.println();
			pw.println("Gantt chart of each Component");
			pw.println(String.join(separator , new String[]{"Product", "Component", "Error/Error Torerance", "Start Time", "Finish Time", "Start Time", "Finish Time", "Start Time", "Finish Time"}));
			this.productList.forEach(p -> {
				String productName = "Product ("+p.getDueDate()+")";
				p.getComponentList().forEach(c -> {
					List<String> baseInfo = new ArrayList<String>();
					baseInfo.add(productName);
					baseInfo.add(c.getName());
					baseInfo.add(String.valueOf(c.getError())+"/"+String.valueOf(c.getErrorTolerance()));
					IntStream.range(0, c.getFinishTimeList().size()).forEach(i -> {
						baseInfo.add(String.valueOf(c.getStartTimeList().get(i)));
						baseInfo.add(String.valueOf(c.getFinishTimeList().get(i)));
					});
					pw.println(String.join(separator ,baseInfo.stream().toArray(String[]::new)));
				});
			});
			// Organization
			pw.println();
			pw.println("Gantt chart of each Resource");
			pw.println(String.join(separator , new String[]{"Team", "Type", "Name", "Start Time", "Finish Time"}));
			this.organization.getTeamList().forEach(t -> {
				String teamName = t.getName();
				
				//Workers
				t.getWorkerList().forEach(w -> {;
					List<String> baseInfo = new ArrayList<String>();
					baseInfo.add(teamName);
					baseInfo.add("Worker");
					baseInfo.add(w.getName());
					IntStream.range(0, w.getAssignedTaskList().size()).forEach(i -> {
						baseInfo.add(String.valueOf(w.getStartTimeList().get(i)));
						baseInfo.add(String.valueOf(w.getFinishTimeList().get(i)));
					});
					pw.println(String.join(separator, baseInfo.stream().toArray(String[]::new)));
				});
				
				//Facilities
				t.getFacilityList().forEach(w -> {
					List<String> baseInfo = new ArrayList<String>();
					baseInfo.add(teamName);
					baseInfo.add("Facility");
					baseInfo.add(w.getName());
					IntStream.range(0, w.getFinishTimeList().size()).forEach(i -> {
						baseInfo.add(String.valueOf(w.getStartTimeList().get(i)));
						baseInfo.add(String.valueOf(w.getFinishTimeList().get(i)));
					});
					pw.println(String.join(separator, baseInfo.stream().toArray(String[]::new)));
				});
			});
			
			pw.close();
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}
}