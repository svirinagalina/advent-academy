package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Paginated metadata projection: listing courses never hydrates component collections. */
@Service
@Transactional(readOnly = true)
public class CourseDirectory {
    private final JdbcTemplate jdbc;
    private final ContentAccessGuard guard;
    private final CurrentUserProvider user;
    public CourseDirectory(JdbcTemplate jdbc, ContentAccessGuard guard, CurrentUserProvider user) { this.jdbc = jdbc; this.guard = guard; this.user = user; }
    public record Item(UUID id,String title,String description,String status) { }
    public record Page(List<Item> items,long total,int page,int size) { }
    public Page page(boolean managed,String status,UUID id,int page,int size) {
        if (page<0 || size<1 || size>100 || !List.of("ALL","DRAFT","PUBLISHED","ARCHIVED").contains(status)) {
            throw new IllegalArgumentException("Invalid course pagination or filter");
        }
        String where=" WHERE true "; var args=new ArrayList<Object>();
        if (!guard.isCurator()) {
            if (managed) {where+="AND owner_id=? ";args.add(user.requireCurrentUserId());}
            else {where+="AND (status<>'DRAFT' OR owner_id=?) ";args.add(user.requireCurrentUserId());}
        }
        if (!status.equals("ALL")) {where+="AND status=? ";args.add(status);}
        if (id!=null) {where+="AND id=? ";args.add(id);}
        Long total=jdbc.queryForObject("SELECT count(*) FROM content.courses"+where,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        var items=jdbc.query("SELECT id,title,description,status FROM content.courses"+where+"ORDER BY created_at,id LIMIT ? OFFSET ?",
                (r,n)->new Item(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4)),args.toArray());
        return new Page(items,total==null?0:total,page,size);
    }
}
