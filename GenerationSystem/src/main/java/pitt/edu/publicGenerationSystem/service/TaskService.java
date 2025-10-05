package pitt.edu.publicGenerationSystem.service;

import org.aspectj.weaver.ast.Not;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pitt.edu.publicGenerationSystem.entity.Task;
import pitt.edu.publicGenerationSystem.entity.TaskFile;
import pitt.edu.publicGenerationSystem.entity.Warehouse;
import pitt.edu.publicGenerationSystem.repository.TaskFileRepository;
import pitt.edu.publicGenerationSystem.repository.TaskRepository;
import pitt.edu.publicGenerationSystem.repository.WarehouseRepository;
import pitt.edu.publicGenerationSystem.response.ApiResponse;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private TaskFileService taskFileService;
    @Autowired
    private RabbitMQService rabbitMQService;
    @Autowired
    private TaskFileRepository taskFileRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;

    @Transactional
    public Task createTask(Long fileId, String processedName, Task.TaskName taskName, String sourcePath, Task.TaskStatus status) {
        Task task = new Task();
        task.setFileId(fileId);
        task.setName(processedName);
        task.setTaskName(taskName);
        task.setSourcePath(sourcePath);
        task.setStatus(status);
        return taskRepository.save(task);
    }

    public List<Task> getTasksByTaskNameAndStatusIn(Task.TaskName taskName, List<Task.TaskStatus> statuses) {
        return taskRepository.findByTaskNameAndStatusIn(
                taskName,
                statuses
        );
    }

    public List<Task> getTasksByTaskNameAndStatus(Task.TaskName taskName, Task.TaskStatus status) {
        return taskRepository.findByTaskNameAndStatus(
                taskName,
                status
        );
    }

    public List<Task> findByFileId(Long fileId) {
        return taskRepository.findByFileId(fileId);
    }

    public Task createTask(Task task) { return taskRepository.save(task); }

    public Task updateTask(Task task) {
        return taskRepository.save(task);
    }

    public ResponseEntity<ApiResponse> fileArchiveFinished(Long taskId, String status) {
        // set task to finish // set task file to archive
        if (status.equals("success")) {
            try {
                Optional<Task> optionalTask = taskRepository.findById(taskId);
                if (optionalTask.isPresent()) {
                    Task task = optionalTask.get();
                    task.setStatus(Task.TaskStatus.COMPLETED);
                    taskRepository.save(task);
                    TaskFile taskFile = taskFileService.findById(task.getFileId());
                    taskFile.setStatus(TaskFile.FileStatus.Archive);
                    taskFileRepository.save(taskFile);
                    // create new noar edf tasks
                    Task newTask = new Task();
                    newTask.setTaskName(Task.TaskName.NoArEDF);
                    newTask.setStatus(Task.TaskStatus.PENDING);
                    newTask.setFileId(task.getFileId());
                    newTask.setName(task.getName());
                    newTask.setSourcePath(task.getSourcePath());
                    newTask = createTask(newTask);

                    // copy file in to task folder
                    Path source = Paths.get("/mnt/nvme1/shared_space/dat", newTask.getSourcePath());
                    Path destination = Paths.get("/mnt/nvme1/shared_space/dat", String.valueOf(newTask.getId()), newTask.getSourcePath());

                    taskFileService.copyFolderWithCleanTarget(source, destination);
                    rabbitMQService.sendTask(newTask);
                    // create new process tasks
                    newTask = new Task();
                    newTask.setTaskName(Task.TaskName.Process);
                    newTask.setStatus(Task.TaskStatus.PENDING);
                    newTask.setFileId(task.getFileId());
                    newTask.setName(task.getName());
                    newTask.setSourcePath(task.getSourcePath());
                    newTask = createTask(newTask);

                    // copy file in to task folder
                    source = Paths.get("/mnt/nvme1/shared_space/dat", newTask.getSourcePath());
                    destination = Paths.get("/mnt/nvme1/shared_space/dat", String.valueOf(newTask.getId()), newTask.getSourcePath());

                    taskFileService.copyFolderWithCleanTarget(source, destination);
                    rabbitMQService.sendTask(newTask);

                    // create new compression tasks
                    newTask = new Task();
                    newTask.setTaskName(Task.TaskName.Compression);
                    newTask.setStatus(Task.TaskStatus.PENDING);
                    newTask.setFileId(task.getFileId());
                    newTask.setName(task.getName());
                    newTask.setSourcePath(task.getSourcePath());
                    newTask = createTask(newTask);
                    rabbitMQService.sendTask(newTask);

                    return ResponseEntity.status(HttpStatus.OK)
                            .body(new ApiResponse<>("ok", "200", "", null));
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponse<>("error", "401", "Task not found", null));
                }
            } catch (Exception e) {
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ApiResponse<>("error", "500", "error", null));
            }
        }  else {
            Optional<Task> tasks = taskRepository.findById(taskId);
            if (tasks.isPresent()) {
                Task task = tasks.get();
                task.setStatus(Task.TaskStatus.FAILED);
                taskRepository.save(task);
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ApiResponse<>("ok", "200", "", null));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>("error", "500", "不存在该任务", null));
            }
        }
    }

    public ResponseEntity<ApiResponse> fileTaskFinished(Long taskId, String status) {
        // set task to finish // set task file to archive
        if (status.equals("success")) {
            try {
                Optional<Task> optionalTask = taskRepository.findById(taskId);
                if (optionalTask.isPresent()) {
                    Task task = optionalTask.get();

                    if (task.getTaskName().equals(Task.TaskName.NoArEDF)) {
                        // create new Compression tasks
                        Task newTask = new Task();
                        newTask.setTaskName(Task.TaskName.Compression);
                        newTask.setStatus(Task.TaskStatus.PENDING);
                        newTask.setFileId(task.getFileId());
                        newTask.setName(task.getName() + "_noar.edf");
                        newTask.setSourcePath(task.getSourcePath() + "_noar.edf");
                        newTask = createTask(newTask);
                        rabbitMQService.sendTask(newTask);
                        return ResponseEntity.status(HttpStatus.OK)
                                .body(new ApiResponse<>("ok", "200", "", null));
                    } else if (task.getTaskName().equals(Task.TaskName.Process)) {
                        // create new ArEDF tasks
                        Task newTask = new Task();
                        newTask.setTaskName(Task.TaskName.ArEDF);
                        newTask.setStatus(Task.TaskStatus.PENDING);
                        newTask.setFileId(task.getFileId());
                        newTask.setName(task.getName());
                        newTask.setSourcePath(task.getSourcePath());
                        newTask = createTask(newTask);
                        // copy file in to task folder
                        Path source = Paths.get("/mnt/nvme1/shared_space/processed_dat", newTask.getSourcePath());
                        Path destination = Paths.get("/mnt/nvme1/shared_space/processed_dat", String.valueOf(newTask.getId()), newTask.getSourcePath());

                        taskFileService.copyFolderWithCleanTarget(source, destination);
                        rabbitMQService.sendTask(newTask);

                        // create new NoArCSV tasks
                        newTask = new Task();
                        newTask.setTaskName(Task.TaskName.NoArCSV);
                        newTask.setStatus(Task.TaskStatus.PENDING);
                        newTask.setFileId(task.getFileId());
                        newTask.setName(task.getName());
                        newTask.setSourcePath(task.getSourcePath());
                        newTask = createTask(newTask);
                        // copy file in to task folder
                        source = Paths.get("/mnt/nvme1/shared_space/processed_dat", newTask.getSourcePath());
                        destination = Paths.get("/mnt/nvme1/shared_space/processed_dat", String.valueOf(newTask.getId()), newTask.getSourcePath());

                        taskFileService.copyFolderWithCleanTarget(source, destination);
                        rabbitMQService.sendTask(newTask);

                        // create new ArCSV tasks
                        newTask = new Task();
                        newTask.setTaskName(Task.TaskName.ArCSV);
                        newTask.setStatus(Task.TaskStatus.PENDING);
                        newTask.setFileId(task.getFileId());
                        newTask.setName(task.getName());
                        newTask.setSourcePath(task.getSourcePath());
                        newTask = createTask(newTask);
                        // copy file in to task folder
                        source = Paths.get("/mnt/nvme1/shared_space/processed_dat", newTask.getSourcePath());
                        destination = Paths.get("/mnt/nvme1/shared_space/processed_dat", String.valueOf(newTask.getId()), newTask.getSourcePath());

                        taskFileService.copyFolderWithCleanTarget(source, destination);
                        rabbitMQService.sendTask(newTask);

                        // clean
                        taskFileService.deleteDirectory(source);
                        return ResponseEntity.status(HttpStatus.OK)
                                .body(new ApiResponse<>("ok", "200", "", null));
                    } else if (task.getTaskName().equals(Task.TaskName.ArEDF)) {
                        // create new Compression tasks
                        Task newTask = new Task();
                        newTask.setTaskName(Task.TaskName.Compression);
                        newTask.setStatus(Task.TaskStatus.PENDING);
                        newTask.setFileId(task.getFileId());
                        newTask.setName(task.getName() + "_ar.edf");
                        newTask.setSourcePath(task.getSourcePath() + "_ar.edf");
                        newTask = createTask(newTask);
                        rabbitMQService.sendTask(newTask);
                        return ResponseEntity.status(HttpStatus.OK)
                                .body(new ApiResponse<>("ok", "200", "", null));
                    } else if (task.getTaskName().equals(Task.TaskName.NoArCSV)) {
                        // create new Compression tasks
                        Task newTask = new Task();
                        newTask.setTaskName(Task.TaskName.Compression);
                        newTask.setStatus(Task.TaskStatus.PENDING);
                        newTask.setFileId(task.getFileId());
                        newTask.setName(task.getName() + "_noar.csv");
                        newTask.setSourcePath(task.getSourcePath() + "_noar.csv");
                        newTask = createTask(newTask);
                        rabbitMQService.sendTask(newTask);
                        return ResponseEntity.status(HttpStatus.OK)
                                .body(new ApiResponse<>("ok", "200", "", null));
                    } else if (task.getTaskName().equals(Task.TaskName.ArCSV)) {
                        // create new Compression tasks
                        Task newTask = new Task();
                        newTask.setTaskName(Task.TaskName.Compression);
                        newTask.setStatus(Task.TaskStatus.PENDING);
                        newTask.setFileId(task.getFileId());
                        newTask.setName(task.getName() + "_ar.csv");
                        newTask.setSourcePath(task.getSourcePath() + "_ar.csv");
                        newTask = createTask(newTask);
                        rabbitMQService.sendTask(newTask);
                        return ResponseEntity.status(HttpStatus.OK)
                                .body(new ApiResponse<>("ok", "200", "", null));
                    } else if (task.getTaskName().equals(Task.TaskName.Compression)) {
                        if(task.getSourcePath().endsWith(".csv")) {
                            // create new Anonymization tasks
                            Task newTask = new Task();
                            newTask.setTaskName(Task.TaskName.Anonymization);
                            newTask.setStatus(Task.TaskStatus.PENDING);
                            newTask.setFileId(task.getFileId());
                            newTask.setName(task.getName());
                            newTask.setSourcePath(task.getSourcePath());
                            newTask = createTask(newTask);
                            rabbitMQService.sendTask(newTask);
                        }
                        return ResponseEntity.status(HttpStatus.OK)
                                .body(new ApiResponse<>("ok", "200", "", null));
                    } else if(task.getTaskName().equals(Task.TaskName.Anonymization)) {
                        return ResponseEntity.status(HttpStatus.OK)
                                .body(new ApiResponse<>("ok", "200", "", null));
                    } else {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(new ApiResponse<>("error", "500", "error", null));
                    }
                }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ApiResponse<>("error", "500", "error", null));
            } catch (Exception e) {
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ApiResponse<>("error", "500", "error", null));
            }
        } else {
            // clean

            // database
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ApiResponse<>("ok", "200", "", null));
        }
    }
    public ResponseEntity<ApiResponse> progressIndex() {
        List<Warehouse> allWarehouses = warehouseRepository.findAllWarehouses();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Warehouse warehouse : allWarehouses) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", warehouse.getId());
            item.put("name", warehouse.getName());
            item.put("status", warehouse.getStatus());
            item.put("start_time", warehouse.getCreatedAt());

            item.put("finished", taskFileService.getNumOfCompletedTasksByWarehouseId(warehouse.getId()));
            item.put("total", taskFileService.getNumOfTasksByWarehouseId(warehouse.getId()));

            result.add(item);
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("ok", "200", "", result));
    }

    public void taskCompletionScheduler() {
        // 如果任务做完7个 complete 就结束 或者有一个失败就失败
        List<TaskFile> taskFiles =  taskFileService.getTaskFileUnfinished();
        for (TaskFile taskFile : taskFiles) {
            List<Task> tasks = findByFileId(taskFile.getId());
            Long count = 0L;
            for (Task task : tasks) {
                if (task.getStatus().equals(Task.TaskStatus.FAILED)) {
                    taskFile.setStatus(TaskFile.FileStatus.FAILED);
                    taskFileRepository.save(taskFile);
                    break;
                } else if (task.getStatus().equals(Task.TaskStatus.COMPLETED)) {
                    count += 1;
                } else {
                    break;
                }
                if (count >= 13) {
                    taskFile.setStatus(TaskFile.FileStatus.COMPLETED);
                    taskFileRepository.save(taskFile);
                }
            }
        }
    }
    public void warehousesCompletionScheduler() {
        warehouseRepository.findAllWarehouses().forEach(warehouse -> {
            boolean hasUnfinishedTasks = !taskFileService.getTaskFileByWarehouseIdAndUnfinished(warehouse.getId()).isEmpty();
            if (!hasUnfinishedTasks) {
                warehouse.setStatus(Warehouse.WarehouseStatus.COMPLETED);
                warehouseRepository.save(warehouse); // 持久化更新
            }
        });
    }
}