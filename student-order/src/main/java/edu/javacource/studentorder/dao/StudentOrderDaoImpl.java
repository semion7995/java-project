package edu.javacource.studentorder.dao;

import edu.javacource.studentorder.config.Config;
import edu.javacource.studentorder.domain.*;
import edu.javacource.studentorder.exception.DaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StudentOrderDaoImpl implements StudentOrderDao
{
    private static final Logger logger = LoggerFactory.getLogger(StudentOrderDaoImpl.class);
    private static final String INSERT_ORDER = "INSERT INTO jc_student_order(" +
            "student_order_status, student_order_date, h_sur_name, h_given_name, h_patronymic, h_date_of_birth, " +
            "h_passport_seria, h_passport_number, h_passport_date, h_passport_office_id, h_post_index, h_street_code, h_building, " +
            "h_extension, h_apartment, h_university_id, h_student_number, w_sur_name, w_given_name, w_patronymic, w_date_of_birth, w_passport_seria, w_passport_number, " +
            "w_passport_date, w_passport_office_id, w_post_index, w_street_code, w_building, w_extension, w_apartment, w_university_id, w_student_number, certificate_id, " +
            "register_office_id, marriage_date)" +
            "VALUES (?, ?, ?, ?, ?, " +
            "?, ?, ?, ?, ?," +
            " ?, ?, ?, ?, ?, " +
            "?, ?, ?, ?, ?," +
            " ?, ?, ?, ?, ?" +
            ", ?, ?, ?, ?, ?," +
            " ?,?,?,?,?);";

    private static final String INSERT_CHILD = "INSERT INTO jc_student_child(student_order_id, c_sur_name, c_given_name, c_patronymic, " +
            "c_date_of_birth, c_certificate_number, c_certificate_date, c_register_office_id, c_post_index, c_street_code, c_building, c_extension, c_apartment)" +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

    private static final String SELECT_ORDERS =
            "select so.* , ro.r_office_area_id, ro.r_office_name, " +
            "po_h.p_office_area_id as h_p_office_area_id, po_h.p_office_name as h_p_office_name, " +
            "po_w.p_office_area_id as w_p_office_area_id, po_w.p_office_name as w_p_office_name " +
            "from jc_student_order so " +
            "inner join jc_register_office ro on ro.r_office_id = so.register_office_id " +
            "inner join jc_passport_office po_h on po_h.p_office_id = so.h_passport_office_id " +
            "inner join jc_passport_office po_w on po_w.p_office_id = so.w_passport_office_id " +
            "where student_order_status = ? order by student_order_date LIMIT ?";
    private static final String SELECT_CHILD =
            "SELECT soc.*, ro.r_office_area_id, ro.r_office_name " +
            "FROM jc_student_child soc " +
            "INNER JOIN jc_register_office ro on ro.r_office_id = soc.c_register_office_id " +
            "WHERE student_order_id IN ";
    private static final String SELECT_ORDERS_FULL = "select so.* , ro.r_office_area_id, ro.r_office_name, " +
            "po_h.p_office_area_id as h_p_office_area_id, po_h.p_office_name as h_p_office_name, " +
            "po_w.p_office_area_id as w_p_office_area_id, po_w.p_office_name as w_p_office_name, " +
            "soc.*, ro_c.r_office_area_id, ro_c.r_office_name " +
            "from jc_student_order so " +
            "inner join jc_register_office ro on ro.r_office_id = so.register_office_id " +
            "inner join jc_passport_office po_h on po_h.p_office_id = so.h_passport_office_id " +
            "inner join jc_passport_office po_w on po_w.p_office_id = so.w_passport_office_id " +
            "INNER JOIN jc_student_child soc on soc.student_order_id = so.student_order_id " +
            "inner join jc_register_office ro_c on ro_c.r_office_id = soc.c_register_office_id " +
            "where student_order_status = ? order by so.student_order_id LIMIT ?";

    private Connection getConnection() throws SQLException {
        return ConnectionBuilder.getConnection();
    }
    @Override
    public Long saveStudentOrder(StudentOrder so) throws DaoException {
//        logger.trace("saveStudentOrder");// в некоторых компаниях принято писать логгер при входе в функцию
        Long result = -1L;
        logger.debug("SO:{}", so);
        //хорошая практика смотреть какие данные были переданы полные не полные
        try (Connection con = getConnection();
             PreparedStatement stmt = con.prepareStatement(INSERT_ORDER, new String[]{"student_order_id"}))
        {
            con.setAutoCommit(false);
            try {
            //Header
            stmt.setInt(1, StudentOrderStatus.START.ordinal());
            stmt.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            //Husband and Wife
            setParamsForAdult(stmt, 3, so.getHusband());
            setParamsForAdult(stmt, 18, so.getWife());
            //Marriage
            stmt.setString(33, so.getMarriageCertificateId());
            stmt.setLong(34, so.getMarriageOffice().getOfficeId());
            stmt.setDate(35, java.sql.Date.valueOf(so.getMarriageDate()));

            stmt.executeUpdate();//возвращает число записей изменённых

            ResultSet gkRs = stmt.getGeneratedKeys();//возвращается набор полей указанный в String[]{}
            if (gkRs.next()){
               result = gkRs.getLong(1);
            }
            gkRs.close();
            saveChildren(con, so, result);
            con.commit();
            }
            catch (SQLException ex){
                con.rollback();
                throw ex;
            }

        } catch (SQLException ex){
            logger.error(ex.getMessage(), ex);
            throw  new DaoException(ex);
        }
        return result;
    }

    private void saveChildren(Connection con, StudentOrder so, Long soId) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement(INSERT_CHILD)){
            int counter = 0;
        for (Child child:so.getChildren()){
            stmt.setLong(1, soId);
            setParamsForChild(stmt,2, child);
            stmt.addBatch();//копим пакет записей
            }
            stmt.executeBatch();//пишем пакет, возвращает массив int[] изменений
        }
    }
    //использую алгоритм увеличения paramIndex увеличения на единицу для рефакторинга методов
    //вынес код в отдельный метод
    private void setParamsForChild(PreparedStatement stmt, int start, Child child) throws SQLException{
        setParamsForPerson(stmt, 2, child);
        stmt.setString(6, child.getCertificateNummber());
        stmt.setDate(7, java.sql.Date.valueOf(child.getIssueDate()));
        stmt.setLong(8, child.getIssueDepartment().getOfficeId());
        setParamsForAddress(stmt, 9,child);
    }
    public static void setParamsForAdult(PreparedStatement stmt, int start, Adult adult) throws SQLException{
        setParamsForPerson(stmt,start,adult);
        stmt.setString(start + 4, adult.getPassportSeria());
        stmt.setString(start + 5, adult.getPassportNumber());
        stmt.setDate(start + 6, java.sql.Date.valueOf(adult.getIssueDate()));
        stmt.setLong(start + 7, adult.getIssueDepartment().getOfficeId());
        setParamsForAddress(stmt,start + 8,adult);
        stmt.setLong(start + 13, adult.getUniversity().getUniversityId());
        stmt.setString(start + 14, adult.getStudentId());
    }
    public static void setParamsForPerson (PreparedStatement stmt, int start, Person person) throws SQLException{
        stmt.setString(start, person.getSurName());
        stmt.setString(start+1, person.getGivenName());
        stmt.setString(start + 2, person.getPatronymic());
        stmt.setDate(start + 3, java.sql.Date.valueOf(person.getDateOfBirth()));
    }
    public static void setParamsForAddress (PreparedStatement stmt, int start, Person person) throws SQLException{
        Address h_address = person.getAddress();
        stmt.setString(start , h_address.getPostCode());
        stmt.setLong(start + 1, h_address.getStreet().getStreetCode());
        stmt.setString(start + 2, h_address.getBuilding());
        stmt.setString(start + 3, h_address.getExtension());
        stmt.setString(start + 4, h_address.getApartment());
    }



    @Override
    public List<StudentOrder> getStudentOrders() throws DaoException {
        return getStudentOrderOneSelect();
//        return getStudentOrderTwoSelect();
    }
        //тут пример получения множества одним запросом но тут нужно исключить дублирующиеся строки(так как детей может быть несколько)
    private List<StudentOrder> getStudentOrderOneSelect() throws DaoException {
        List<StudentOrder> result = new LinkedList<>();
        try (Connection con = getConnection();
             PreparedStatement stmt = con.prepareStatement(SELECT_ORDERS_FULL)){
            Map<Long, StudentOrder> maps = new HashMap<>();  //создал проверяльщик, а есть - ли такая студ заявка?

            stmt.setInt(1, StudentOrderStatus.START.ordinal());

            int limit = Integer.parseInt(Config.getProperty(Config.DB_LIMIT));

            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            int counter = 0;

            while (rs.next()){
                Long soId = rs.getLong("student_order_id");
                if (!maps.containsKey(soId)) {  //убеждаюсь, что заявка в мапе есть избавляемся от дублирующихся строк
                    StudentOrder so = getFullStudentOrder(rs);
                    result.add(so);
                    maps.put(soId, so);
                }
                StudentOrder so = maps.get(soId);
                so.addChild(fillChild(rs));//fillChild метод формирующий мои объекты по данным из базы
                counter++;
            }
            if (counter >=limit){
                result.remove(result.size() - 1);//удаляю последнюю семью чтобы не разорвать список семей вытаскивая лимитированное количество записей
            }
            rs.close();
        } catch (SQLException ex){
            logger.error(ex.getMessage(), ex);
            throw new DaoException(ex);
        }
        return result;
    }
//Формирую множество записей из student_order, затем в findChildren получаю множество детей согласно student_order_id
    private List<StudentOrder> getStudentOrderTwoSelect() throws DaoException {
        List<StudentOrder> result = new LinkedList<>();
        try (Connection con = getConnection();
             PreparedStatement stmt = con.prepareStatement(SELECT_ORDERS)){
            stmt.setInt(1, StudentOrderStatus.START.ordinal());
            stmt.setInt(2, Integer.parseInt(Config.getProperty(Config.DB_LIMIT)));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()){
                StudentOrder so = getFullStudentOrder(rs);

                result.add(so);
            }
            findChildren(con, result);//тут я делаю SELECT_CHILD для получения списка детей, заполняю их данными из БД
            //и аккуратно складываю в поле объекта StudentOrder
            rs.close();
        } catch (SQLException ex){
            logger.error(ex.getMessage(), ex);
            throw new DaoException(ex);
        }
        return result;
    }

    private StudentOrder getFullStudentOrder(ResultSet rs) throws SQLException {
        //Заполняю свои объекты данными из БД
        StudentOrder so = new StudentOrder();

        fillStudentOrder(rs, so);
        fillMarriage(rs, so);

        so.setHusband(fillAdult(rs, "h_"));
        so.setWife(fillAdult(rs, "w_"));
        return so;
    }

    private void fillStudentOrder(ResultSet rs, StudentOrder so) throws SQLException {
        so.setStudentOrderId(rs.getLong("student_order_id"));
        so.setStudentOrderDateTime(rs.getTimestamp("student_order_date").toLocalDateTime());
        so.setStudentOrderStatus(StudentOrderStatus.fromValue(rs.getInt("student_order_status")));

    }

    private void fillMarriage(ResultSet rs, StudentOrder so) throws SQLException{
        so.setMarriageCertificateId(rs.getString("certificate_id"));
        so.setMarriageDate(rs.getDate("marriage_date").toLocalDate());
        Long roId = rs.getLong("register_office_id");
        String areaId = rs.getString("r_office_area_id");
        String officeName = rs.getString("r_office_name");
        RegisterOffice ro = new RegisterOffice(roId, areaId, officeName);
        so.setMarriageOffice(ro);

    }

    private Adult fillAdult(ResultSet rs, String pref) throws SQLException {
        Adult adult = new Adult(rs.getString(pref + "sur_name"), rs.getString(pref + "given_name"),
                rs.getString(pref + "patronymic"), rs.getDate(pref + "date_of_birth").toLocalDate());
        adult.setPassportSeria(rs.getString(pref + "passport_seria"));
        adult.setPassportNumber(rs.getString(pref + "passport_number"));
        adult.setIssueDate(rs.getDate(pref + "passport_date").toLocalDate());

        Long areaId = rs.getLong(pref + "passport_office_id");
        String poAreaId = rs.getString(pref +"p_office_area_id");
        String poName = rs.getString(pref + "p_office_name");

        PassportOffice po = new PassportOffice(areaId, poAreaId, poName);
        Address address = new Address(rs.getString(pref + "post_index"),
                new Street(rs.getLong(pref + "street_code"), ""),
                rs.getString(pref + "building"), rs.getString(pref + "extension"), rs.getString(pref + "apartment"));
        University university = new University(rs.getLong(pref + "university_id"), "");
        adult.setIssueDepartment(po);
        adult.setAddress(address);
        adult.setUniversity(university);
        adult.setStudentId(rs.getString(pref + "student_number"));
//        System.out.println(adult);
        return adult;
    }

    private void findChildren(Connection con, List<StudentOrder> result) throws SQLException {
        String cl = "(" + result.stream().map(so -> String.valueOf(so.getStudentOrderId()))
                .collect(Collectors.joining(",")) + ")";

        Map<Long, StudentOrder> maps = result.stream().collect(Collectors.
                toMap(so -> so.getStudentOrderId(), so -> so));

        try(PreparedStatement stmt = con.prepareStatement(SELECT_CHILD + cl)){
            ResultSet rs = stmt.executeQuery();
            while (rs.next()){
                Child ch = fillChild(rs);
                StudentOrder so = maps.get(rs.getLong("student_order_id"));
                so.addChild(ch);
            }
        }
    }

    private Child fillChild(ResultSet rs) throws SQLException {
        String surName = rs.getString("c_sur_name");
        String givenName = rs.getString("c_given_name");
        String patronymic = rs.getString("c_patronymic");
        LocalDate dateOfBirth = rs.getDate("c_date_of_birth").toLocalDate();
        Child child = new Child(surName,givenName ,patronymic ,dateOfBirth);
        String certificateNum = rs.getString("c_certificate_number");

        RegisterOffice ro = new RegisterOffice(rs.getLong("c_register_office_id"),
                rs.getString("r_office_area_id"), rs.getString("r_office_name"));

        child.setCertificateNummber(certificateNum);
        child.setIssueDate(rs.getDate("c_certificate_date").toLocalDate());
        child.setIssueDepartment(ro);

        Street street = new Street(rs.getLong("c_street_code"), "");
        Address address = new Address(rs.getString("c_post_index"),
                street, rs.getString("c_building"), rs.getString("c_extension"), rs.getString("c_apartment"));
        child.setAddress(address);
//        System.out.println(child);
        return child;

    }
}
