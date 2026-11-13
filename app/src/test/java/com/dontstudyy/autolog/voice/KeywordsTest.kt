package com.dontstudyy.autolog.voice

import com.dontstudyy.autolog.trip.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeywordsTest {
    @Test
    fun 거래처_미팅_업무_키워드는_일반업무() {
        assertEquals(Answer(Category.BUSINESS, "거래처 미팅"), parsePurpose("거래처 미팅"))
        assertEquals(Answer(Category.BUSINESS, "업무"), parsePurpose("업무"))
        assertEquals(Answer(Category.BUSINESS, "고객 회의"), parsePurpose("고객 회의"))
    }

    @Test
    fun 출근_퇴근은_출퇴근() {
        assertEquals(Answer(Category.COMMUTE, "출근"), parsePurpose("출근"))
        assertEquals(Answer(Category.COMMUTE, "퇴근"), parsePurpose("퇴근이야"))
        assertEquals(Answer(Category.COMMUTE, "출퇴근"), parsePurpose("출퇴근"))
    }

    @Test
    fun 개인은_비업무() {
        assertEquals(Answer(Category.PRIVATE, "개인 용무"), parsePurpose("개인 용무"))
        assertEquals(Answer(Category.PRIVATE, "개인 용무"), parsePurpose("개인적인 일이에요"))
    }

    @Test
    fun 개인과_업무가_같이_나오면_개인이_우선() {
        assertEquals(Answer(Category.PRIVATE, "개인 용무"), parsePurpose("개인 업무"))
    }

    @Test
    fun 알_수_없는_말이나_빈_문자열은_null() {
        assertNull(parsePurpose("안녕하세요"))
        assertNull(parsePurpose(""))
        assertNull(parsePurpose("   "))
    }

    @Test
    fun Gleo_목적_문자열은_해석되지_않아도_일반업무로_기록() {
        assertEquals(Answer(Category.BUSINESS, "샘플 납품"), purposeFromIntent("샘플 납품"))
        assertEquals(Answer(Category.PRIVATE, "개인 용무"), purposeFromIntent("개인 용무"))
        assertNull(purposeFromIntent(""))
        assertNull(purposeFromIntent(null))
    }
}
