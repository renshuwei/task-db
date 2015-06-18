/*
 * Copyright (C) 2015 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.task.management.controller;

import juzu.*;
import juzu.impl.common.Tools;
import juzu.request.SecurityContext;
import org.exoplatform.commons.juzu.ajax.Ajax;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.task.dao.OrderBy;
import org.exoplatform.task.domain.Comment;
import org.exoplatform.task.domain.Project;
import org.exoplatform.task.domain.Task;
import org.exoplatform.task.exception.AbstractEntityException;
import org.exoplatform.task.exception.ProjectNotFoundException;
import org.exoplatform.task.management.model.CommentModel;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.TaskParser;
import org.exoplatform.task.service.TaskService;
import org.exoplatform.task.service.UserService;
import org.exoplatform.task.utils.CommentUtils;
import org.exoplatform.task.utils.ProjectUtil;
import org.exoplatform.task.utils.TaskUtil;
import org.exoplatform.task.utils.UserUtils;
import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author <a href="mailto:tuyennt@exoplatform.com">Tuyen Nguyen The</a>.
 */
public class TaskController {

  @Inject
  TaskService taskService;

  @Inject
  ProjectService projectService;

  @Inject
  TaskParser taskParser;

  @Inject
  OrganizationService orgService;

  @Inject
  UserService userService;

  @Inject
  ResourceBundle bundle;

  @Inject
  @Path("detail.gtmpl")
  org.exoplatform.task.management.templates.detail detail;

  @Inject
  @Path("comments.gtmpl")
  org.exoplatform.task.management.templates.comments comments;

  @Inject
  @Path("projectTaskListView.gtmpl")
  org.exoplatform.task.management.templates.projectTaskListView taskListView;

  @Resource
  @Ajax
  @MimeType.HTML
  public Response detail(Long id, SecurityContext securityContext) {

    try {

      Task task = taskService.getTaskById(id); //Can throw TaskNotFoundException

      StringBuilder coWorkerDisplayName = new StringBuilder();
      if(task.getCoworker() != null && task.getCoworker().size() > 0) {
        for(String userName : task.getCoworker()) {
          User user = orgService.getUserHandler().findUserByName(userName); //Can throw Exception
          if(user != null) {
            if(coWorkerDisplayName.length() > 0) {
              coWorkerDisplayName.append(", ");
            }
            coWorkerDisplayName.append(UserUtils.getDisplayName(user));
          }
        }
      }

      String assignee = "";
      if(task.getAssignee() != null) {
        User user = orgService.getUserHandler().findUserByName(task.getAssignee()); //Can throw Exception
        if(user != null) {
          assignee = UserUtils.getDisplayName(user);
        }
      }

      long commentCount = taskService.getNbOfCommentsByTask(task);

      List<Comment> cmts = taskService.getCommentsByTask(task, 0, 2);
      List<CommentModel> comments = new ArrayList<CommentModel>(cmts.size());
      for(Comment c : cmts) {
        org.exoplatform.task.model.User u = userService.loadUser(c.getAuthor());
        comments.add(new CommentModel(c, u, CommentUtils.formatMention(c.getComment(), userService)));
      }

      org.exoplatform.task.model.User currentUser = userService.loadUser(securityContext.getRemoteUser());

      return detail.with()
          .task(task)
          .assigneeName(assignee)
          .coWokerDisplayName(coWorkerDisplayName.toString())
          .commentCount(commentCount)
          .comments(comments)
          .currentUser(currentUser)
          .ok().withCharset(Tools.UTF_8);

    } catch (AbstractEntityException e) {
      return Response.status(e.getHttpStatusCode()).body(e.getMessage());
    } catch (Exception ex) {// NOSONAR
      return Response.status(500).body(ex.getMessage());
    }
  }

  @Resource
  @Ajax
  @MimeType.JSON
  public Response clone(Long id) {

    try {

      Task newTask = taskService.cloneTaskById(id); //Can throw TaskNotFoundException

      JSONObject json = new JSONObject();
      json.put("id", newTask.getId()); //Can throw JSONException
      return Response.ok(json.toString());

    } catch (AbstractEntityException e) {
      return Response.status(e.getHttpStatusCode()).body(e.getMessage());
    } catch (JSONException ex) {
      return Response.status(500).body(ex.getMessage());
    }
  }

  @Resource
  @Ajax
  @MimeType.JSON
  public Response delete(Long id) {

    try {

      taskService.deleteTaskById(id);//Can throw TaskNotFoundException

      JSONObject json = new JSONObject();
      json.put("id", id); //Can throw JSONException
      return Response.ok(json.toString());

    } catch (AbstractEntityException e) {
      return Response.status(e.getHttpStatusCode()).body(e.getMessage());
    } catch (JSONException ex) {
      return Response.status(500).body(ex.getMessage());
    }
  }

  @Resource
  @Ajax
  @MimeType("text/plain")
  public Response saveTaskInfo(Long taskId, String name, String[] value) {

    try {

      taskService.updateTaskInfo(taskId, name, value); //Can throw TaskNotFoundException & ParameterEntityException & StatusNotFoundException
      return Response.ok("Update successfully");

    } catch (AbstractEntityException e) {
      return Response.status(e.getHttpStatusCode()).body(e.getMessage());
    }
  }

  @Resource
  @Ajax
  @MimeType("text/plain")
  public Response updateCompleted(Long taskId, Boolean completed) {

    try {

      taskService.updateTaskCompleted(taskId, completed); //Can throw TaskNotFoundException & ParameterEntityException
      return Response.ok("Update successfully");

    } catch (AbstractEntityException e) {
      return Response.status(e.getHttpStatusCode()).body(e.getMessage());
    }
  }

  @Resource
  @Ajax
  @MimeType.JSON
  public Response comment(Long taskId, String comment, SecurityContext securityContext) {

    String currentUser = securityContext.getRemoteUser();
    if (currentUser == null || currentUser.isEmpty()) {
      return Response.status(401);
    }

    try {

      Comment cmt = taskService.addCommentToTaskId(taskId, currentUser, comment); //Can throw TaskNotFoundException

      //TODO:
      CommentModel model = new CommentModel(cmt, userService.loadUser(cmt.getAuthor()), CommentUtils.formatMention(cmt.getComment(), userService));

      DateFormat df = new SimpleDateFormat("MMM dd, yyyy HH:mm");

      JSONObject json = new JSONObject();
      json.put("id", model.getId()); //Can throw JSONException (same for all #json.put methods below)
      JSONObject user = new JSONObject();
      user.put("username", model.getAuthor().getUsername());
      user.put("displayName", model.getAuthor().getDisplayName());
      user.put("avatar", model.getAuthor().getAvatar());
      json.put("author", user);
      json.put("comment", model.getComment());
      json.put("formattedComment", model.getFormattedComment());
      json.put("createdTime", model.getCreatedTime().getTime());
      json.put("createdTimeString", df.format(model.getCreatedTime()));
      return Response.ok(json.toString()).withCharset(Tools.UTF_8);

    } catch (AbstractEntityException e) {
      return Response.status(e.getHttpStatusCode()).body(e.getMessage());
    }
    catch (JSONException ex) {
      return Response.status(500).body(ex.getMessage());
    }
  }

  @Resource
  @Ajax
  @MimeType.HTML
  public Response loadAllComments(Long taskId, SecurityContext securityContext) {

    try {

      List<Comment> cmts = taskService.getCommentsByTaskId(taskId, 0, -1); //Can throw TaskNotFoundException

      List<CommentModel> listComments = new ArrayList<CommentModel>(cmts.size());
      for(Comment cmt : cmts) {
        org.exoplatform.task.model.User u = userService.loadUser(cmt.getAuthor());
        listComments.add(new CommentModel(cmt, u, CommentUtils.formatMention(cmt.getComment(), userService)));
      }

      org.exoplatform.task.model.User currentUser = userService.loadUser(securityContext.getRemoteUser());

      return comments.with()
          .commentCount(cmts.size())
          .comments(listComments)
          .currentUser(currentUser)
          .ok()
          .withCharset(Tools.UTF_8);

    } catch (AbstractEntityException e) {
      return Response.status(e.getHttpStatusCode()).body(e.getMessage());
    }

  }

  @Resource
  @Ajax
  @MimeType("text/plain")
  public Response deleteComment(Long commentId) {

    try {

      taskService.deleteCommentById(commentId); //Can throw CommentNotFoundException
      return Response.ok("Delete comment successfully!");

    } catch (AbstractEntityException e) {
      return Response.status(e.getHttpStatusCode()).body(e.getMessage());
    }

  }

  @Resource
  @Ajax
  @MimeType.HTML
  public Response listTasks(Long projectId, String keyword, String groupBy, String orderBy,
                            SecurityContext securityContext) {
    Project project = null;
    List<Task> tasks;

    String currentUser = securityContext.getRemoteUser();
    if (currentUser == null || currentUser.isEmpty()) {
      return Response.status(401);
    }

    OrderBy order = null;
    if(orderBy != null && !orderBy.trim().isEmpty()) {
      order = "title".equals(orderBy) ? new OrderBy.ASC(orderBy) : new OrderBy.DESC(orderBy);
    }

    //Get Tasks in good order
    if(projectId == ProjectUtil.INCOMING_PROJECT_ID) {
      tasks = taskService.getIncomingTasksByUser(currentUser, order);
    }
    else if (projectId == ProjectUtil.TODO_PROJECT_ID) {
      tasks = taskService.getToDoTasksByUser(currentUser, order);
    }
    else {
      try {
        tasks = projectService.getTasksWithKeywordByProjectId(projectId, order, keyword);
        if (projectId > 0) {
          project = projectService.getProjectById(projectId);          
        }
      } catch (ProjectNotFoundException e) {
        return Response.notFound("Impossible to get tasks for project with ID: " + e.getEntityId());
      }
    }

    //Group Tasks
    Map<String, org.exoplatform.task.model.User> userMap = null;
    Map<String, List<Task>> groupTasks = new HashMap<String, List<Task>>();
    if(groupBy != null && !groupBy.isEmpty()) {
      groupTasks = TaskUtil.groupTasks(tasks, groupBy);
      if("assignee".equalsIgnoreCase(groupBy)) {
        userMap = new HashMap<String, org.exoplatform.task.model.User>();
        for(String assignee : groupTasks.keySet()) {
          org.exoplatform.task.model.User user = userService.loadUser(assignee);
          userMap.put(assignee, user);
        }
      }
    }
    if(groupTasks.isEmpty()) {
      groupTasks.put("", tasks);
    }
    
    long taskNum;
    if (projectId <= 0) {
      taskNum = taskService.getTaskNum(currentUser, projectId);
    } else {
      taskNum = taskService.getTaskNum(null, projectId);
    }

    return taskListView
        .with()
        .currentProjectId(projectId)
        .project(project)
        .tasks(tasks)
        .taskNum(taskNum)
        .groupTasks(groupTasks)
        .keyword(keyword == null ? "" : keyword)
        .groupBy(groupBy == null ? "" : groupBy)
        .orderBy(orderBy == null ? "" : orderBy)
        .bundle(bundle)
        .set("userMap", userMap)
        .ok()
        .withCharset(Tools.UTF_8);
  }

  @Resource(method = HttpMethod.POST)
  @Ajax
  @MimeType.HTML
  public Response createTask(Long projectId, String taskInput, SecurityContext securityContext) {

    if(taskInput == null || taskInput.isEmpty()) {
      return Response.content(406, "Task input must not be null or empty");
    }

    String currentUser = securityContext.getRemoteUser();
    if (currentUser == null || currentUser.isEmpty()) {
      return Response.status(401);
    }

    Task task = taskParser.parse(taskInput);
    task.setCreatedBy(currentUser);

    //Project task
    if(projectId > 0) {
      try {
        projectService.createTaskToProjectId(projectId, task);
      } catch (AbstractEntityException e) {
        return Response.status(e.getHttpStatusCode()).body(e.getMessage());
      }
    }
    //Incoming Task
    else {
      taskService.createTask(task);
    }

    return listTasks(projectId, "", "", "", securityContext);
  }

}
