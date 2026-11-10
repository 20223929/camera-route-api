package com.example.cameraroute.task;
import com.example.cameraroute.dto.CameraResponse;
import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.service.CameraRouteService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@Service
public class CameraRouteTaskService {
    private final CameraRouteTaskRepository taskRepository;
    private final CameraRouteService routeService;
    private final ObjectMapper objectMapper;
    public CameraRouteTaskService(CameraRouteTaskRepository taskRepository,CameraRouteService routeService,ObjectMapper objectMapper) {
        this.taskRepository=taskRepository;this.routeService=routeService;this.objectMapper=objectMapper;
    }
    public String submit(RouteCameraRequest request) {
        String taskId=UUID.randomUUID().toString().replace("-","");
        taskRepository.create(taskId,request.getRouteId());return taskId;
    }
    @Async("routeTaskExecutor")
    public void execute(String taskId,RouteCameraRequest request) { runTask(taskId,request); }
    public void runTask(String taskId,RouteCameraRequest request) {
        try {
            taskRepository.markRunning(taskId);
            List<CameraResponse> cameras=routeService.findCameras(request);
            taskRepository.complete(taskId,objectMapper.writeValueAsString(cameras),cameras.size());
        } catch(Exception ex) {
            String message=ex.getMessage()==null?"路线查询任务执行失败":ex.getMessage();
            taskRepository.fail(taskId,message.length()>1000?message.substring(0,1000):message);
        }
    }
    public Map<String,Object> getStatus(String taskId) {
        Map<String,Object> status=taskRepository.find(taskId);status.remove("created_at");status.remove("updated_at");return status;
    }
    public PageResult getResult(String taskId,int pageNum,int pageSize) {
        if(pageNum<1) throw new IllegalArgumentException("pageNum 必须大于等于 1");
        if(pageSize<1||pageSize>1000) throw new IllegalArgumentException("pageSize 必须在 1 至 1000 之间");
        Map<String,Object> status=taskRepository.find(taskId);
        if(!"COMPLETED".equals(String.valueOf(status.get("status")))) throw new IllegalArgumentException("任务尚未完成，当前状态："+status.get("status"));
        String json=taskRepository.findResultJson(taskId);
        try {
            List<CameraResponse> all=json==null?Collections.<CameraResponse>emptyList():objectMapper.readValue(json,new TypeReference<List<CameraResponse>>(){});
            long offset=((long)pageNum-1L)*(long)pageSize;
            int from=(int)Math.min(offset,(long)all.size()),to=Math.min(from+pageSize,all.size());
            return new PageResult(all.size(),all.subList(from,to));
        } catch(Exception ex) { throw new IllegalStateException("读取任务结果失败",ex); }
    }
    public static final class PageResult {
        private final int total; private final List<CameraResponse> data;
        public PageResult(int total,List<CameraResponse> data){this.total=total;this.data=data;}
        public int getTotal(){return total;} public List<CameraResponse> getData(){return data;}
    }
}